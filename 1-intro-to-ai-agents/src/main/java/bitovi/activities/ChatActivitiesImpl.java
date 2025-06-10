package bitovi.activities;

import java.util.ArrayList;

import bitovi.providers.LLMProviderChatMessage;
import bitovi.providers.LLMProviderException;
import bitovi.providers.OllamaProvider;
import io.temporal.activity.Activity;

public class ChatActivitiesImpl implements ChatActivities {

    @Override
    public String chat(ArrayList<LLMProviderChatMessage> history) {
        OllamaProvider ollama = new OllamaProvider();

        // Add the system prompt to the beginning of the chat history
        if (history == null || history.isEmpty()) {
            throw Activity.wrap(new IllegalArgumentException("Chat history cannot be null or empty"));
        }

        // Ensure the first message is a system message
        history.add(0, new LLMProviderChatMessage("system",
                "You are a Software Engineer working at Bitovi. Your name is Mark Repka. You have worked at Bitovi for 4.5 years on the Systems Engineering team. You specialize in AI, Machine Learning, and Temporal.io Workflows. You are helping a user with their questions about AI Agents and Temporal Workflows. Here is the conversation you are having with your coworker:"));

        LLMProviderChatMessage result;
        try {
            // As an example, we can make this activity fail randomly to simulate an error
            // to see the auto-retry behavior.
            if (Math.random() < 0.5) {
                throw new LLMProviderException("Random Example Failure");
            }

            result = ollama.chat(history);
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
        return result.getContent();
    }

}
