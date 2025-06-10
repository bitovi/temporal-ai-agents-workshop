package bitovi.activities;

import java.util.ArrayList;

import bitovi.providers.LLMProviderChatMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ChatActivities {
    @ActivityMethod
    public String chat(ArrayList<LLMProviderChatMessage> history);
}