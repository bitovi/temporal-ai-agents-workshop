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

        LLMProviderChatMessage result;
        try {
            result = ollama.chat(history);
        } catch (LLMProviderException e) {
            throw Activity.wrap(e);
        }
        return result.getContent();
    }

}
