package bitovi.workflows.AgentGoal;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;

import bitovi.common.Agent;
import bitovi.common.Config;
import bitovi.common.DataTypes;
import bitovi.common.DataTypes.PromptSummaryRecord;
import bitovi.workflows.AgentGoal.AgentGoalTypes.AgentGoalConversationEntry;
import bitovi.workflows.AgentGoal.AgentGoalTypes.AgentGoalConversationHistory;
import bitovi.workflows.AgentGoal.activities.AgentGoalActivities;
import bitovi.workflows.AgentGoal.activities.AgentGoalActivities.AgentToolPlannerInput;
import bitovi.workflows.AgentGoal.activities.AgentGoalActivities.ListModelContextProtocolToolsResult;
import bitovi.workflows.AgentGoal.helpers.AgentGoalHelpers;
import bitovi.workflows.AgentGoal.helpers.AgentSelectionHelper;
import bitovi.workflows.AgentGoal.helpers.AgentToolArgument;
import bitovi.workflows.AgentGoal.helpers.AgentToolDefinition;
import bitovi.workflows.AgentGoal.helpers.AgentToolPlannerResult;
import io.temporal.workflow.Workflow;

/**
 * Workflow that manages tool execution with user confirmation and conversation
 * history.
 */
public class AgentGoalWorkflowImpl implements AgentGoalWorkflow {

    private AgentGoalConversationHistory conversationHistory = new AgentGoalConversationHistory(
            new ArrayList<AgentGoalConversationEntry>());
    private ArrayList<String> promptQueue = new ArrayList<String>();
    private String conversationSummary = null;
    private boolean chatEnded = false;
    private AgentToolPlannerResult toolData = null;
    private boolean confirmed = false; // indicates that we have confirmation to proceed to run tool
    private ArrayList<JSONObject> toolResults = new ArrayList<JSONObject>();
    private Agent goal = null;
    private boolean showToolArgsConfirmation = true; // set from env file in activity lookup_wf_env_settings
    private boolean multiGoalMode = false; // set from env file in activity lookup_wf_env_settings
    private ListModelContextProtocolToolsResult mcpToolsInfo = null; // stores complete MCP tools result

    private Logger logger = Workflow.getLogger("AgentGoalWorkflowImpl");

    // This is the activity stub of the INTERFACE, not the implementation.
    private final AgentGoalActivities agentGoalActivites = Workflow.newActivityStub(AgentGoalActivities.class,
            Config.getDefaultActivityOptions());

    @Override
    public AgentGoalConversationHistory run(CombinedWorkflowInput combinedInput) {
        AgentGoalWorkflowParams params = combinedInput.toolParams();
        this.goal = combinedInput.agentGoal();

        lookupWorkflowEnvSettings(combinedInput);

        if (this.goal != null && this.goal.mcpServerDefinition() != null) {
            loadModelContextProtocolTools();
        }

        if (params != null && params.conversationSummary() != null) {
            // self.add_message("conversation_summary", params.conversation_summary)
            // self.conversation_summary = params.conversation_summary
            addMessage("conversation_summary", params.conversationSummary());
            this.conversationSummary = params.conversationSummary();
        }

        if (params != null && params.promptQueue() != null) {
            // self.prompt_queue.extend(params.prompt_queue)
            this.promptQueue.addAll(params.promptQueue());
        }

        // If the goal is not set, we need to change it to the default goal.
        if (this.goal == null) {
            logger.info("No goal set. Changing to default goal: goal_choose_agent_type");
            this.changeGoal("goal_choose_agent_type");
        }

        logger.info("Starting AgentGoalWorkflowImpl with initial goal: " + this.goal);

        boolean waitingForConfirm = false;
        String currentTool = null;

        while (true) {
            // Check if the user has disconnected or if there are prompts in the queue.
            Workflow.await(() -> this.chatEnded || this.promptQueue.size() > 0 || this.confirmed);

            if (this.chatShouldEnd()) {
                logger.info("Ending chat due to user disconnection.");
                return this.conversationHistory; // Return the conversation history when the chat ends.
            }

            if (this.readyForToolExecution(waitingForConfirm, currentTool)) {
                waitingForConfirm = executeTool(currentTool);
                continue;
            }

            // If there are prompts in the queue, process them.
            if (this.promptQueue.size() > 0) {
                String prompt = this.promptQueue.remove(0);
                Workflow.getLogger("AgentGoalWorkflowImpl")
                        .info("Processing user prompt from queue. Prompt: " + prompt);

                if (this.isUserPrompt(prompt)) {
                    // Add the user prompt to the conversation history.
                    this.addMessage("user", prompt);
                    DataTypes.ValidationInputRecord validationInput = new DataTypes.ValidationInputRecord(prompt,
                            this.conversationHistory, this.goal);

                    DataTypes.ValidationResultRecord validationResult = agentGoalActivites
                            .validateUserInput(validationInput);

                    if (!validationResult.isValid()) {
                        logger.error("Prompt validation failed:" + validationResult.message());
                        this.addMessage("agent", validationResult.message());
                        continue; // Skip to the next iteration to wait for more input.
                    }
                }

                // If valid, proceed with generating the context and prompt
                logger.info("User input validated successfully.");

                String contextInstructions = generateGenAIPrompt(this.goal, this.conversationHistory,
                        this.multiGoalMode, this.toolData, this.mcpToolsInfo);

                // Execute the tool_planner with the instructions.
                AgentToolPlannerResult agentToolPlannerResult = agentGoalActivites
                        .agentToolPlanner(new AgentToolPlannerInput(prompt, contextInstructions));

                agentToolPlannerResult.forceConfirm = this.showToolArgsConfirmation;
                this.toolData = agentToolPlannerResult;

                String nextStep = agentToolPlannerResult.nextStep;
                currentTool = agentToolPlannerResult.tool;

                logger.info("nextStep: " + nextStep + ", currentTool: " + currentTool);

                if (nextStep.equals("confirm")) {
                    Object args = toolData.args;
                    Object missingArgs = agentGoalActivites.handleMissingArgs(currentTool, args, toolData,
                            promptQueue);

                    if (missingArgs != null) {
                        // If there are missing arguments, we need to prompt the user for them.
                        System.out.println("Missing arguments for tool execution: " + missingArgs);
                        continue;
                    }

                    waitingForConfirm = true;

                    if (this.showToolArgsConfirmation) {
                        this.confirmed = false; // Reset confirmation status for the next tool execution.
                        logger.info("Waiting for user confirmation to execute tool: " + currentTool);
                    } else {
                        this.confirmed = true; // Automatically confirm if showToolArgsConfirmation is false.
                    }
                } else if (nextStep.equals("pick-new-goal")) {
                    logger.info("All steps completed. Need to pick a new goal.");
                    this.changeGoal("goal_choose_agent_type");
                } else if (nextStep.equals("done")) {
                    this.addMessage("agent", this.toolData.response);
                    return this.conversationHistory; // Return the conversation history when done.
                } else {
                    String responseText = agentToolPlannerResult.response;
                    logger.info("Agent response: " + responseText);
                    this.addMessage("agent", responseText);

                    // AgentGoalHelpers.continueAsNewIfNeeded(this.conversationHistory,
                    // this.promptQueue, this.goal,
                    // MAX_TURNS_BEFORE_CONTINUE, this::addMessage);

                    if (conversationHistory.messages().size() >= Config.MAX_TURNS_BEFORE_CONTINUE) {
                        PromptSummaryRecord promptSummary = AgentGoalHelpers
                                .promptSummaryWithHistory(conversationHistory);

                        AgentToolPlannerResult result = agentGoalActivites.agentToolPlanner(new AgentToolPlannerInput(
                                promptSummary.actualPrompt(), promptSummary.contextInstructions()));

                        // Add the summary to the conversation history.
                        addMessage("conversation_summary", result.response);

                        AgentGoalWorkflowParams newParams = new AgentGoalWorkflowParams(
                                result.response,
                                this.promptQueue);

                        // Continue as new summarizing the conversation history, keeping the same goal,
                        // and copying the prompt queue over.
                        Workflow.continueAsNew(new CombinedWorkflowInput(newParams, this.goal));
                    }
                }
            }
        }
    }

    @Override
    public void prompt(String prompt) {
        if (this.chatEnded) {
            logger.info("Message dropped due to chat closed: " + prompt);
            return; // Ignore prompts if the chat has ended.
        }
        this.promptQueue.add(prompt);
    }

    @Override
    public void disconnect() {
        logger.info("Received user signal: disconnect");
        this.chatEnded = true;
    }

    @Override
    public void confirm() {
        logger.info("Received user signal: confirmation");
        this.confirmed = true;
    }

    @Override
    public AgentGoalConversationHistory getConversationHistory() {
        return this.conversationHistory;
    }

    @Override
    public Agent getAgentGoal() {
        return this.goal;
    }

    @Override
    public String getConversationSummary() {
        return this.conversationSummary;
    }

    @Override
    public AgentToolPlannerResult getLatestToolData() {
        return this.toolData;
    }

    public void addMessage(String actor, String content) {
        logger.info("Adding message to conversation history: " + actor + ": " + content);
        // This method adds a message to the conversation history.
        this.conversationHistory.messages().add(new AgentGoalConversationEntry(actor, content));
    }

    private boolean isUserPrompt(String prompt) {
        if (prompt.startsWith("###")) {
            return false;
        }

        return true;
    }

    private void changeGoal(String newGoal) {
        if (this.goal != null) {
            logger.info("Changing goal from '" + this.goal.toString() + "' to '" + newGoal + "'");

            // Loop over the goal list, if the 'id' matches the incoming new goal, set the
            // listed goal as the current goal.
            for (Agent possibleGoal : AgentSelectionHelper.getGoalList()) {
                if (possibleGoal.id.equals(newGoal)) {
                    this.goal = possibleGoal;
                    logger.info("Goal set to '" + newGoal + "'");
                    break;
                }
            }
        }
    }

    private boolean chatShouldEnd() {
        if (this.chatEnded) {
            logger.info("Chat should end due to user disconnection.");
            return true; // End the chat if the user has disconnected.
        }

        return false;
    }

    private boolean readyForToolExecution(boolean waitingForConfirm, String currentTool) {
        if (this.confirmed && waitingForConfirm && currentTool != null && this.toolData != null) {
            return true;
        }
        return false;
    }

    private boolean executeTool(String tool) {
        this.confirmed = false; // Reset confirmation status for the next tool execution.
        boolean waitingForConfirm = false; // Set to true to wait for user confirmation before executing the tool.

        AgentToolPlannerResult confirmedToolData = AgentToolPlannerResult.copy(this.toolData);
        confirmedToolData.nextStep = "user_confirmed_tool_run";

        addMessage("user_confirmed_tool_run", confirmedToolData.toString());

        AgentGoalHelpers.handleToolExecution(tool, this.toolData, this.toolResults, this.promptQueue,
                this.goal);

        // set new goal if we should
        if (this.toolResults != null && this.toolResults.size() > 0) {
            JSONObject lastToolResult = this.toolResults.get(this.toolResults.size() - 1);

            // Check if "ChangeGoal" is in the values AND "new_goal" is in the keys
            // if (
            // "ChangeGoal" in self.tool_results[-1].values()
            // and "new_goal" in self.tool_results[-1].keys()
            // ):
            // new_goal = self.tool_results[-1].get("new_goal")
            // self.change_goal(new_goal)
            boolean hasChangeGoalInValues = false;
            for (String key : lastToolResult.keySet()) {
                Object value = lastToolResult.get(key);
                if (value != null && value.toString().contains("ChangeGoal")) {
                    hasChangeGoalInValues = true;
                    break;
                }
            }

            if (hasChangeGoalInValues && lastToolResult.has("new_goal")) {
                String newGoal = lastToolResult.getString("new_goal");
                this.changeGoal(newGoal);
                return false; // No need to wait for confirmation, goal changed.
            }
            // Check if "ListAgents" is in the values AND current goal is not
            // "goal_choose_agent_type"
            // elif (
            // "ListAgents" in self.tool_results[-1].values()
            // and self.goal.id != "goal_choose_agent_type"
            // ):
            // self.change_goal("goal_choose_agent_type")
            else {
                boolean hasListAgentsInValues = false;
                for (String key : lastToolResult.keySet()) {
                    Object value = lastToolResult.get(key);
                    if (value != null && value.toString().contains("ListAgents")) {
                        hasListAgentsInValues = true;
                        break;
                    }
                }

                if (hasListAgentsInValues && !this.goal.id.equals("goal_choose_agent_type")) {
                    this.changeGoal("goal_choose_agent_type");
                }
            }
        }

        return waitingForConfirm; // Return the waitingForConfirm status to continue or not.
    }

    private void lookupWorkflowEnvSettings(CombinedWorkflowInput combinedInput) {
        // Set the defaults for the workflow environment.
        DataTypes.EnvLookupInputRecord env_lookup_input = new DataTypes.EnvLookupInputRecord("SHOW_CONFIRM", true);

        DataTypes.EnvLookupOutputRecord env_lookup_output = agentGoalActivites.getWorkflowEnvSettings(env_lookup_input);
        this.showToolArgsConfirmation = env_lookup_output.showConfirm();
        this.multiGoalMode = env_lookup_output.multiGoalMode();
    }

    private void loadModelContextProtocolTools() {
        if (this.goal == null) {
            logger.info("No goal found. Skipping loading of model context protocol tools.");
            return; // No goal, nothing to load.
        }

        if (this.goal.mcpServerDefinition() == null) {
            logger.info(
                    "No MCP server definition found in the goal. Skipping loading of model context protocol tools.");
            return; // No MCP server definition, nothing to load.
        }

        logger.info(
                "Loading model context protocol tools from MCP server definition: " + this.goal.mcpServerDefinition());

        List<String> includeTools = this.goal.mcpServerDefinition().includedTools();

        ListModelContextProtocolToolsResult mcpToolsResult = agentGoalActivites
                .listModelContextProtocolTools(this.goal.mcpServerDefinition(), includeTools);
        if (mcpToolsResult.success()) {
            ArrayList<AgentToolDefinition> toolsInfo = mcpToolsResult.tools();
            logger.info("Successfully loaded model context protocol tools: " + toolsInfo);

            this.mcpToolsInfo = mcpToolsResult;

            for (AgentToolDefinition tool : toolsInfo) {
                logger.info("Registered MCP Tool: " + tool.getToolName() + " - " + tool.getToolDescription());
                this.goal.registerTool(tool);
            }

        } else {
            var errorMessage = mcpToolsResult.error();
            logger.error("Failed to load model context protocol tools: " + errorMessage);
            // continue without MCP tools loaded.
        }
    }

    private String generateGenAIPrompt(Agent agentGoal, AgentGoalConversationHistory conversationHistory,
            boolean multiGoalMode, AgentToolPlannerResult toolData, ListModelContextProtocolToolsResult mcpToolsInfo) {
        StringBuilder instructions = new StringBuilder();

        instructions.append("You are an AI agent that helps fill required arguments for the tools described below.");
        instructions.append(
                "You must respond with valid JSON ONLY, using the schema provided in the instructions.");
        instructions.append("\n");
        instructions.append("=== Conversation History ===\n");
        instructions.append("This is the ongoing history to determine which tool and arguments to gather:\n\n");
        instructions.append("*BEGIN CONVERSATION HISTORY*");
        for (AgentGoalConversationEntry message : conversationHistory.messages()) {
            instructions.append("\n");
            instructions.append(message.actor());
            instructions.append(": ");
            instructions.append(message.response());
        }
        instructions.append("\n");
        instructions.append("*END CONVERSATION HISTORY*\n");

        instructions.append("REMINDER: You should use the conversation history to infer arguments for the tools.\n");

        instructions.append("=== Tools Definitions ===\n");
        if (agentGoal != null) {
            instructions.append("There are " + agentGoal.tools.size() + " available tools:\n");

            for (AgentToolDefinition tool : agentGoal.tools) {
                instructions.append(tool.getToolName() + "\n");
            }
            instructions.append("\n");

            instructions.append("Goal:");
            instructions.append(agentGoal.agentDescription);
            instructions.append("\n");
            instructions.append("Gather the necessary information for each tool in the sequence described above.");
            instructions.append("Only ask for arguments listed below. Do not add extra arguments.");

            for (AgentToolDefinition tool : agentGoal.tools) {
                instructions
                        .append("Tool Name: " + tool.getToolName() + "\n");
                instructions.append("   Description: " + tool.getToolDescription() + "\n");
                instructions.append("   Arguments:\n");
                for (AgentToolArgument arg : tool.getToolArguments()) {
                    instructions
                            .append("       " + arg.getName() + "(" + arg.getType() + ") : " + arg.getDescription()
                                    + "\n");
                }

                instructions.append("   Required Arguments: ");
                for (AgentToolArgument arg : tool.getToolArguments()) {
                    if (arg.isRequired()) {
                        instructions.append(arg.getName() + ", ");
                    }
                }
                instructions.append("\n");
            }
            instructions.append("\n");

            instructions
                    .append("When all required args for a tool are known, you can propose next='confirm' to run it.\n");

        } else {
            instructions.append("There is no goal set yet, so no tools have been loaded.\n");
        }

        // JSON Format Instructions
        instructions.append("=== Instructions for JSON Generation ===\n");
        instructions.append("Your JSON format must be:\n");

        instructions.append(responseFormat());

        instructions.append("1) If any required argument is missing, set next='question' and ask the user.\n");
        instructions.append(
                "2) If all required arguments are known, set next='confirm' and specify the tool. The user will confirm before the tool is run.\n");
        instructions.append(
                "3) If no more tools are needed (user_confirmed_tool_run has been run for all), set next='done' and tool=''.\n");
        instructions.append("4) response should be short and user-friendly.\n\n");

        instructions.append("Guardrails (always remember!)\n");
        instructions.append("1) If any required argument is missing, set next='question' and ask the user.\n");
        instructions.append("1) ALWAYS ask a question in your response if next='question'.\n");
        instructions.append("2) ALWAYS set next='confirm' if you have arguments\n ");
        instructions.append("And respond with \"let\'s proceed with <tool> (and any other useful info)\" \n ");
        instructions.append("DON'T set next='confirm' if you have a question to ask.\n");
        instructions.append("EXAMPLE: If you have a question to ask, set next='question' and ask the user.\n");
        instructions.append("3) You can carry over arguments from one tool to another.\n ");
        instructions.append(
                "EXAMPLE: If you asked for an account ID, then use the conversation history to infer that argument ");
        instructions.append("going forward.");
        instructions.append("4) If ListAgents in the conversation history is force_confirm='False', you MUST check ");
        instructions.append(
                "if the current tool contains userConfirmation. If it does, please ask the user to confirm details ");
        instructions.append("with the user. userConfirmation overrides force_confirm='False'.\n");
        instructions.append(
                "EXAMPLE: (force_confirm='False' AND userConfirmation exists on tool) Would you like me to <run tool> ");
        instructions.append("with the following details: <details>?\n");

        return instructions.toString();
    }

    private String responseFormat() {
        return """
                {
                    "response": "<plain text>",
                    "next": "<question|confirm|pick-new-goal|done>",
                    "tool": "tool_name or null",
                    "args": {
                        "<arg1>": "<value1 or null>",
                        "<arg2>": "<value2 or null>",
                        ...
                    }
                }
                """;
    }
}