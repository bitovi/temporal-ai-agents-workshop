package bitovi.workflows.Chat;

import java.util.ArrayList;

import bitovi.Config;
import bitovi.DataTypes;
import bitovi.DataTypes.MessageRecord;
import bitovi.workflows.Chat.activities.ChatActivities;
import io.temporal.workflow.Workflow;

public class ChatWorkflowImpl implements ChatWorkflow {
    private ArrayList<DataTypes.MessageRecord> history = new ArrayList<DataTypes.MessageRecord>();
    private ArrayList<String> promptQueue = new ArrayList<String>();

    private String lastResponse = null;

    private final ChatActivities activities = Workflow.newActivityStub(ChatActivities.class,
            Config.getDefaultActivityOptions());

    @Override
    public void run() {
        while (true) {
            // Block current thread until the unblocking condition is evaluated to true
            Workflow.await(() -> !promptQueue.isEmpty());
            if (!promptQueue.isEmpty()) {
                // Call the activity to process the prompt. If the user sent multiple prompts,
                // combine them into a single prompt.

                StringBuilder combined = new StringBuilder();
                while (!promptQueue.isEmpty()) {
                    combined.append(promptQueue.remove(0)).append(" ");
                }

                String prompt = combined.toString().trim();
                if (prompt.isEmpty()) {
                    continue; // Skip to the next iteration if the prompt is empty
                }

                history.add(new MessageRecord("user", prompt));

                String response = activities.chat(history);
                if (response == null || response.isEmpty()) {
                    continue; // Skip to the next iteration if the response is empty
                }

                lastResponse = response;

                // Add the assistant response back to the conversation
                history.add(new MessageRecord("assistant", response));
            }

        }
    }

    @Override
    public void prompt(String prompt) {
        promptQueue.add(prompt);
    }

    @Override
    public String getLastResponse() {
        return lastResponse;
    }
}
