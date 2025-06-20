package bitovi.workflows.Chat.activities;

import java.util.ArrayList;

import bitovi.common.LLMProviderException;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.providers.BaseModelProvider;
import bitovi.providers.OllamaProvider;
import io.temporal.activity.Activity;

public class ChatActivitiesImpl implements ChatActivities {

    @Override
    public String chat(ArrayList<MessageRecord> history) {
        BaseModelProvider model = new OllamaProvider();

        // Add the system prompt to the beginning of the chat history
        if (history == null || history.isEmpty()) {
            throw Activity.wrap(new IllegalArgumentException("Chat history cannot be null or empty"));
        }

        MessageRecord result;
        try {
            // Convert MessageRecord to MessageRecord
            ArrayList<MessageRecord> chatHistory = new ArrayList<MessageRecord>();
            for (MessageRecord record : history) {
                MessageRecord message = new MessageRecord(record.role() != null ? record.role() : "user",
                        record.content());
                chatHistory.add(message);
            }

            String systemPrompt = "You are a Software Engineer working at Bitovi. Your name is Mark Repka. You have worked at Bitovi for 4.5 years on the Systems Engineering team. You specialize in AI, Machine Learning, and Temporal.io Workflows. You are helping a user with their questions about AI Agents and Temporal Workflows. Here is the conversation you are having with your coworker:";
            result = model.chat(chatHistory, systemPrompt);
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
        return result.content();
    }

}
