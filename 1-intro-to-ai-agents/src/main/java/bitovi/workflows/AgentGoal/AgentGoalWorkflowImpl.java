package bitovi.workflows.AgentGoal;

import java.time.Duration;
import java.util.ArrayList;

import bitovi.Config;
import bitovi.activities.AgentGoalActivities;
import bitovi.providers.Transform;
import bitovi.records.MessageRecord;
import bitovi.records.ValidationResultRecord;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

public class AgentGoalWorkflowImpl implements AgentGoalWorkflow {

    private ArrayList<MessageRecord> conversationHistory = new ArrayList<MessageRecord>();

    private ArrayList<MessageRecord> promptQueue = new ArrayList<MessageRecord>();

    private String currentGoal = null;

    private boolean disconnected = false;

    // This is the activity stub of the INTERFACE, not the implementation.
    private final AgentGoalActivities activities = Workflow.newActivityStub(AgentGoalActivities.class,
            Config.getDefaultActivityOptions());

    @Override
    public void prompt(String prompt) {
        this.promptQueue.add(new MessageRecord("user", prompt));
    }

    @Override
    public void disconnect() {
        this.disconnected = true;
    }

    @Override
    public void run() {
        while (true) {
            // Check if the user has disconnected or if there are prompts in the queue.
            boolean hasInput = Workflow.await(Duration.ofSeconds(120),
                    () -> this.disconnected || this.promptQueue.size() > 0);

            // If no input is received within 2 minutes, throw an exception.
            if (!hasInput) {
                throw ApplicationFailure.newFailure(
                        "WaitForName signal is not received within 2 minutes.", "signal-timeout");
            }

            // If the user has disconnected, log the event and exit the workflow.
            if (this.disconnected) {
                Workflow.getLogger("AgentGoalWorkflowImpl").info("User disconnected.");
                return;
            }

            // If there are prompts in the queue, process them.
            if (this.promptQueue.size() > 0) {
                Workflow.getLogger("AgentGoalWorkflowImpl").info("Processing user prompt from queue.");
                MessageRecord prompt = this.promptQueue.remove(0);

                // Add the user prompt to the conversation history.
                this.conversationHistory.add((prompt));

                // Validate the user input.
                ValidationResultRecord validationResult = activities.validateUserInput(prompt,
                        this.conversationHistory, this.currentGoal);
                if (!validationResult.isValid()) {
                    Workflow.getLogger("AgentGoalWorkflowImpl")
                            .error("User input validation failed: " + validationResult.message());

                    this.conversationHistory
                            .add(new MessageRecord("assistant", validationResult.message()));
                    continue; // Skip to the next iteration to wait for more input.
                }

                Workflow.getLogger("AgentGoalWorkflowImpl").info("User input validated successfully.");

                // Process the user input and determine the next steps.
                String instructions = activities.generateInstructions(prompt, this.conversationHistory,
                        this.currentGoal);

                // Execute the tool_planner with the instructions.
                String conversation = Transform.LLMProviderChatMessagesToString(this.conversationHistory);
                var toolPlannerResult = activities.toolPlanner(instructions, conversation);

                System.out.println("Tool Planner Result: " + toolPlannerResult.toString());
            }
        }

    }
}