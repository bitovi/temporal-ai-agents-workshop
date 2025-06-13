package bitovi.workflows.Chat.activities;

import java.util.ArrayList;

import bitovi.DataTypes.MessageRecord;
import bitovi.providers.LLMProviderException;
import bitovi.providers.OllamaProvider;
import io.temporal.activity.Activity;

public class ChatActivitiesImpl implements ChatActivities {

    @Override
    public String chat(ArrayList<MessageRecord> history) {
        OllamaProvider ollama = new OllamaProvider();

        // Add the system prompt to the beginning of the chat history
        if (history == null || history.isEmpty()) {
            throw Activity.wrap(new IllegalArgumentException("Chat history cannot be null or empty"));
        }

        // Ensure the first message is a system message
        history.add(0, new MessageRecord("system",
                "You are a Software Engineer working at Bitovi. Your name is Mark Repka. You have worked at Bitovi for 4.5 years on the Systems Engineering team. You specialize in AI, Machine Learning, and Temporal.io Workflows. You are helping a user with their questions about AI Agents and Temporal Workflows. Here is the conversation you are having with your coworker:"));

        MessageRecord result;
        try {
            // As an example, we can make this activity fail randomly to simulate an error
            // to see the auto-retry behavior.
            if (Math.random() < 0.5) {
                throw new LLMProviderException("Random Example Failure");
            }

            // Convert MessageRecord to MessageRecord
            ArrayList<MessageRecord> chatHistory = new ArrayList<MessageRecord>();
            for (MessageRecord record : history) {
                MessageRecord message = new MessageRecord(record.role(), record.content());
                chatHistory.add(message);
            }

            result = ollama.chat(chatHistory);
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
        return result.content();
    }

}
