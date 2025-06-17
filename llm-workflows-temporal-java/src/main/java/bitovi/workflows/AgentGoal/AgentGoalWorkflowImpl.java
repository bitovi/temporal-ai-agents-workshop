package bitovi.workflows.AgentGoal;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

import bitovi.common.Agent;
import bitovi.common.Config;
import bitovi.common.DataTypes;
import bitovi.common.Unknown;
import bitovi.common.DataTypes.AgentGoalWorkflowParams;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.common.DataTypes.PromptSummaryRecord;
import bitovi.common.DataTypes.ToolDataRecord;
import bitovi.workflows.AgentGoal.activities.AgentGoalActivities;
import bitovi.workflows.AgentGoal.activities.AgentGoalActivities.AgentToolPlannerResult;
import bitovi.workflows.AgentGoal.helpers.AgentGoalHelpers;
import bitovi.workflows.AgentGoal.helpers.AgentSelectionHelper;
import bitovi.workflows.AgentGoal.helpers.AgentToolDefinition;
import io.temporal.workflow.Workflow;

/**
 * Workflow that manages tool execution with user confirmation and conversation
 * history.
 */
public class AgentGoalWorkflowImpl implements AgentGoalWorkflow {

    private static final int MAX_TURNS_BEFORE_CONTINUE = 250; // Maximum turns before continuing the workflow.

    private ArrayList<DataTypes.MessageRecord> conversationHistory = new ArrayList<DataTypes.MessageRecord>();
    private ArrayList<DataTypes.MessageRecord> promptQueue = new ArrayList<DataTypes.MessageRecord>();
    // private ArrayList<String> goalList = new ArrayList<String>();

    private String conversationSummary = null;
    private boolean disconnected = false;
    private Agent goal = null;
    private String currentTool = null; // This will hold the name of the tool to be executed.
    private boolean waitingForConfirm = false;
    private boolean showToolArgsConfirmation = true;
    private boolean multiGoalMode = false;
    private ToolDataRecord toolData = null;
    private List<Unknown> toolResults = new ArrayList<Unknown>();
    private DataTypes.AgentGoalWorkflowParams params = null;
    private List<AgentToolDefinition> toolsInfo = null;
    private DataTypes.ListModelContextProtocolToolsResult mcpToolsInfo = null;

    private Logger logger = Workflow.getLogger("AgentGoalWorkflowImpl");

    // This is a flag to indicate whether the user has confirmed the tool execution.
    private boolean confirmed = false;

    // This is the activity stub of the INTERFACE, not the implementation.
    private final AgentGoalActivities agentGoalActivites = Workflow.newActivityStub(AgentGoalActivities.class,
            Config.getDefaultActivityOptions());

    @Override
    public ArrayList<DataTypes.MessageRecord> run(DataTypes.CombinedWorkflowInput combinedInput) {
        this.params = combinedInput.toolParams();
        this.goal = combinedInput.agentGoal();

        lookupWorkflowEnvSettings(combinedInput);

        if (this.goal.mcpServerDefinition() != null) {
            loadModelContextProtocolTools();
        }

        if (this.params != null && this.params.conversationSummary() != null) {
            // self.add_message("conversation_summary", params.conversation_summary)
            // self.conversation_summary = params.conversation_summary
            addMessage("conversation_summary", this.params.conversationSummary());
            this.conversationSummary = this.params.conversationSummary();
        }

        if (this.params != null && this.params.promptQueue() != null) {
            // self.prompt_queue.extend(params.prompt_queue)
            this.promptQueue.addAll(this.params.promptQueue());
        }

        logger.info("Starting AgentGoalWorkflowImpl with initial goal: " + this.goal);

        // boolean waitingForConfirm = false;
        String currentTool = null;

        while (true) {
            // Check if the user has disconnected or if there are prompts in the queue.
            Workflow.await(() -> this.disconnected || this.promptQueue.size() > 0 || this.confirmed);

            if (this.chatShouldEnd()) {
                logger.info("Ending chat due to user disconnection.");
                return this.conversationHistory; // Return the conversation history when the chat ends.
            }

            if (this.readyForToolExecution(this.waitingForConfirm, this.currentTool)) {
                this.waitingForConfirm = executeTool(this.currentTool);
                continue;
            }

            // If there are prompts in the queue, process them.
            if (this.promptQueue.size() > 0) {
                Workflow.getLogger("AgentGoalWorkflowImpl").info("Processing user prompt from queue.");
                DataTypes.MessageRecord prompt = this.promptQueue.remove(0);

                // Add the user prompt to the conversation history.
                this.addMessage("user", prompt.content());

                DataTypes.ValidationInputRecord validationInput = new DataTypes.ValidationInputRecord(prompt,
                        this.conversationHistory, this.goal);

                DataTypes.ValidationResultRecord validationResult = agentGoalActivites
                        .validateUserInput(validationInput);

                if (!validationResult.isValid()) {
                    Workflow.getLogger("AgentGoalWorkflowImpl")
                            .error("User input validation failed: " + validationResult.message());

                    this.addMessage("assistant", validationResult.message());
                    continue; // Skip to the next iteration to wait for more input.
                }

                /// --------
                Workflow.getLogger("AgentGoalWorkflowImpl").info("User input validated successfully.");

                // Process the user input and determine the next steps.
                String contextInstructions = agentGoalActivites.generateGenAIPrompt(prompt, this.conversationHistory,
                        this.goal);

                // Execute the tool_planner with the instructions.
                AgentToolPlannerResult toolPlannerResult = agentGoalActivites
                        .agentToolPlanner(prompt.content(), contextInstructions);

                System.out.println("Tool Planner Result: " + toolPlannerResult.toString());

                toolPlannerResult.structuredOutput().put("force_confirm",
                        this.showToolArgsConfirmation ? "true" : "false");

                String nextStep = toolPlannerResult.structuredOutput().get("next");
                currentTool = toolPlannerResult.structuredOutput().get("tool");

                System.out.println("Next Step: " + nextStep + ", Current Tool: " + currentTool);
                if (nextStep.equals("confirm")) {
                    String args = toolPlannerResult.structuredOutput().get("args");
                    var missingArgs = agentGoalActivites.handleMissingArgs(currentTool, args, toolData,
                            promptQueue);
                    if (missingArgs != null) {
                        // If there are missing arguments, we need to prompt the user for them.
                        System.out.println("Missing arguments for tool execution: " + missingArgs);
                        continue;
                    }

                    this.waitingForConfirm = true;

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
                    this.addMessage("agent", this.toolData);
                    return this.conversationHistory; // Return the conversation history when done.
                } else {
                    this.addMessage("agent", this.toolData);

                    // AgentGoalHelpers.continueAsNewIfNeeded(this.conversationHistory,
                    // this.promptQueue, this.goal,
                    // MAX_TURNS_BEFORE_CONTINUE, this::addMessage);

                    if (conversationHistory.size() >= MAX_TURNS_BEFORE_CONTINUE) {
                        PromptSummaryRecord promptSummary = AgentGoalHelpers
                                .promptSummaryWithHistory(conversationHistory);

                        DataTypes.ToolPlannerResult result = agentGoalActivites.toolPlanner(
                                promptSummary.contextInstructions(),
                                promptSummary.actualPrompt());

                        // Add the summary to the conversation history.
                        addMessage("conversation_summary", result.toString());

                        AgentGoalWorkflowParams newParams = new DataTypes.AgentGoalWorkflowParams(
                                result.toString(),
                                this.promptQueue);

                        // Continue as new summarizing the conversation history, keeping the same goal,
                        // and copying the prompt queue over.
                        Workflow.continueAsNew(new DataTypes.CombinedWorkflowInput(newParams, this.goal));
                    }
                }
            }
        }
    }

    @Override
    public void prompt(String prompt) {
        if (this.disconnected) {
            logger.info("Message dropped due to chat closed: " + prompt);
            return; // Ignore prompts if the chat has ended.
        }
        this.promptQueue.add(new DataTypes.MessageRecord("user", prompt));
    }

    @Override
    public void disconnect() {
        logger.info("Received user signal: disconnect");
        this.disconnected = true;
    }

    @Override
    public void confirm() {
        logger.info("Received user signal: confirmation");
        this.confirmed = true;
    }

    @Override
    public ArrayList<MessageRecord> getConversationHistory() {
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
    public ToolDataRecord getLatestToolData() {
        return this.toolData;
    }

    public void addMessage(String actor, String content) {
        // This method adds a message to the conversation history.
        DataTypes.MessageRecord message = new DataTypes.MessageRecord(actor, content);
        this.conversationHistory.add(message);
        logger.info("Added message to conversation history: " + message);
    }

    public void addMessage(String actor, DataTypes.ToolDataRecord toolData) {
        throw new UnsupportedOperationException(
                "This method is not implemented yet. Please implement the logic to handle ToolDataRecord.");
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
        if (this.disconnected) {
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
        boolean waitingForConfirm = false;

        var confirmedToolData = new ToolDataRecord("user-confirmed-tool-run", this.toolData.tool(),
                this.toolData.args(), this.toolData.response(), this.toolData.forceConfirm());

        addMessage("user_confirmed_tool_run", confirmedToolData);

        this.toolResults = AgentGoalHelpers.handleToolExecution(tool, this.toolData, this.promptQueue,
                this.goal);

        return false;
    }

    private void lookupWorkflowEnvSettings(DataTypes.CombinedWorkflowInput combinedInput) {
        // Set the defaults for the workflow environment.
        DataTypes.EnvLookupInputRecord env_lookup_input = new DataTypes.EnvLookupInputRecord("SHOW_CONFIRM", true);

        DataTypes.EnvLookupOutputRecord env_lookup_output = agentGoalActivites.getWorkflowEnvSettings(env_lookup_input);
        this.showToolArgsConfirmation = env_lookup_output.showConfirm();
        this.multiGoalMode = env_lookup_output.multiGoalMode();
    }

    private void loadModelContextProtocolTools() {
        if (this.goal.mcpServerDefinition() == null) {
            logger.info(
                    "No MCP server definition found in the goal. Skipping loading of model context protocol tools.");
            return; // No MCP server definition, nothing to load.
        }

        logger.info(
                "Loading model context protocol tools from MCP server definition: " + this.goal.mcpServerDefinition());

        List<String> includeTools = this.goal.mcpServerDefinition().includedTools();

        DataTypes.ListModelContextProtocolToolsResult mcpToolsResult = agentGoalActivites
                .listModelContextProtocolTools(this.goal.mcpServerDefinition(), includeTools);
        if (mcpToolsResult.success()) {
            this.toolsInfo = mcpToolsResult.tools();
            logger.info("Successfully loaded model context protocol tools: " + this.toolsInfo);

            // Store the mcp tools for user in prompt generation.
            this.mcpToolsInfo = mcpToolsResult;

            for (AgentToolDefinition tool : this.toolsInfo) {
                logger.info("Registered MCP Tool: " + tool.getToolName() + " - " + tool.getToolDescription());
                this.goal.registerTool(tool);
            }

        } else {
            var errorMessage = mcpToolsResult.error();
            logger.error("Failed to load model context protocol tools: " + errorMessage);
            // continue without MCP tools loaded.
        }
    }
}