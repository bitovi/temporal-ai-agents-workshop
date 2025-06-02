package demo.workflows.AgentGoal;

import java.time.Duration;
import java.util.ArrayList;

import demo.Config;
import demo.activities.AgentGoalActivities;
import demo.activities.helpers.ValidationResult;
import demo.workflows.AgentGoal.helpers.ChatMessage;
import demo.workflows.AgentGoal.helpers.ConversationHistory;
import demo.workflows.AgentGoal.helpers.Role;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

public class AgentGoalWorkflowImpl implements AgentGoalWorkflow {

    private ConversationHistory conversationHistory = new ConversationHistory();

    private ArrayList<ChatMessage> promptQueue = new ArrayList<ChatMessage>();

    private String currentGoal = null;

    private boolean disconnected = false;

    // This is the activity stub of the INTERFACE, not the implementation.
    private final AgentGoalActivities activities = Workflow.newActivityStub(AgentGoalActivities.class,
            Config.getDefaultActivityOptions());

    @Override
    public void prompt(String prompt) {
        this.promptQueue.add(new ChatMessage(Role.USER, prompt));
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
                ChatMessage prompt = this.promptQueue.remove(0);

                // Add the user prompt to the conversation history.
                this.conversationHistory.addMessage((prompt));

                // Validate the user input.
                ValidationResult validationResult = activities.validateUserInput(prompt,
                        this.conversationHistory.getConversation(), this.currentGoal);
                if (!validationResult.isValid()) {
                    Workflow.getLogger("AgentGoalWorkflowImpl")
                            .error("User input validation failed: " + validationResult.getMessage());

                    this.conversationHistory.addMessage(new ChatMessage(Role.ASSISTANT, validationResult.getMessage()));
                    continue; // Skip to the next iteration to wait for more input.
                }

                Workflow.getLogger("AgentGoalWorkflowImpl").info("User input validated successfully.");

                // Process the user input and determine the next steps.

            }

        }

    }

}
