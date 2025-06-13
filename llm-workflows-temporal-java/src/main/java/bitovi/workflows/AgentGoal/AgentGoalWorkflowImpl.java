package bitovi.workflows.AgentGoal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import bitovi.common.Config;
import bitovi.common.DataTypes;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.common.DataTypes.ToolDataRecord;
import bitovi.workflows.AgentGoal.activities.AgentGoalActivities;
import io.qdrant.client.grpc.Collections.Datatype;
import io.temporal.workflow.Workflow;

/**
 * Workflow that manages tool execution with user confirmation and conversation
 * history.
 */
public class AgentGoalWorkflowImpl implements AgentGoalWorkflow {

    private ArrayList<DataTypes.MessageRecord> conversationHistory = new ArrayList<DataTypes.MessageRecord>();
    private ArrayList<DataTypes.MessageRecord> promptQueue = new ArrayList<DataTypes.MessageRecord>();
    private ArrayList<String> goalList = new ArrayList<String>();

    private String conversationSummary = null;
    private boolean disconnected = false;
    private String currentGoal = null;
    private String currentTool = null; // This will hold the name of the tool to be executed.
    private boolean waitingForConfirm = false;
    private ToolDataRecord toolData = null;

    private Logger logger = Workflow.getLogger("AgentGoalWorkflowImpl");

    // This is a flag to indicate whether the user has confirmed the tool execution.
    private boolean confirmed = false;

    // This is the activity stub of the INTERFACE, not the implementation.
    private final AgentGoalActivities activities = Workflow.newActivityStub(AgentGoalActivities.class,
            Config.getDefaultActivityOptions());

    @Override
    public ArrayList<DataTypes.MessageRecord> run() {
        while (true) {
            // Check if the user has disconnected or if there are prompts in the queue.
            Workflow.await(() -> this.disconnected || this.promptQueue.size() > 0);

            if (this.chatShouldEnd()) {
                logger.info("Ending chat due to user disconnection.");
                return this.conversationHistory; // Return the conversation history when the chat ends.
            }

            if (this.readyForToolExecution()) {
                this.waitingForConfirm = activities.executeTool(this.currentTool, this.toolData);
                continue;
            }

            // If there are prompts in the queue, process them.
            if (this.promptQueue.size() > 0) {
                Workflow.getLogger("AgentGoalWorkflowImpl").info("Processing user prompt from queue.");
                DataTypes.MessageRecord prompt = this.promptQueue.remove(0);

                // Add the user prompt to the conversation history.
                this.conversationHistory.add((prompt));

                DataTypes.ValidationInputRecord validationInput = new DataTypes.ValidationInputRecord(prompt, this.conversationHistory, this.currentGoal);
                DataTypes.ValidationResultRecord validationResult = activities.validateUserInput(validationInput);
                if (!validationResult.isValid()) {
                    Workflow.getLogger("AgentGoalWorkflowImpl")
                            .error("User input validation failed: " + validationResult.message());

                    this.conversationHistory
                            .add(new DataTypes.MessageRecord("assistant", validationResult.message()));
                    continue; // Skip to the next iteration to wait for more input.
                }

                Workflow.getLogger("AgentGoalWorkflowImpl").info("User input validated successfully.");

                // Process the user input and determine the next steps.
                String instructions = activities.generateInstructions(prompt, this.conversationHistory,
                        this.currentGoal);

                // Execute the tool_planner with the instructions.
                var toolPlannerResult = activities.toolPlanner(instructions, this.conversationHistory);

                System.out.println("Tool Planner Result: " + toolPlannerResult.toString());
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
    public String getAgentGoal() {
        return this.currentGoal;
    }

    @Override
    public String getConversationSummary() {
        return this.conversationSummary;
    }

    @Override
    public ToolDataRecord getLatestToolData() {
        return this.toolData;
    }

    private void addMessage(String actor, String content) {
        // This method adds a message to the conversation history.
        DataTypes.MessageRecord message = new DataTypes.MessageRecord(actor, content);
        this.conversationHistory.add(message);
        logger.info("Added message to conversation history: " + message);
    }

    private void addMessage(String actor, Map<String, Object> content) {
        // This method adds a message to the conversation history with a map content.
        DataTypes.MessageRecord message = new DataTypes.MessageRecord(actor, content.toString());
        this.conversationHistory.add(message);
        logger.info("Added message to conversation history: " + message);
    }

    private void changeGoal(String newGoal) {
        if (this.currentGoal != null) {
            logger.info("Changing goal from '" + this.currentGoal + "' to '" + newGoal + "'");
        } else {
            logger.info("Setting initial goal to '" + newGoal + "'");
        }
        this.currentGoal = newGoal;
        this.goalList.add(newGoal);
    }

    private boolean chatShouldEnd() {
        if (this.disconnected) {
            logger.info("Chat should end due to user disconnection.");
            return true; // End the chat if the user has disconnected.
        }

        return false;
    }

    private boolean readyForToolExecution() {
        if (this.confirmed && this.currentTool != null && this.toolData != null) {
            return true;
        }

        return false;
    }

    private boolean isUserPrompt(String prompt) {
        if (prompt.startsWith("###")) {
            return false;
        }

        return true;
    }

    private boolean executeTool(String tool) {
        this.confirmed = false; // Reset confirmation status for the next tool execution.
        this.toolData = new ToolDataRecord(DataTypes.NextStep.USER_CONFIRMED_TOOL_RUN, tool,
                new HashMap<String, String>(), "");
        return false;
    }

    private void loadModelContextProtocolTools() {
        return; // This method is a placeholder for loading model context protocol tools.
    }
}