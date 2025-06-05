package bitovi.activities;

import java.util.ArrayList;

import bitovi.activities.helpers.ValidationResult;
import bitovi.providers.LLMProviderChatMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgentGoalActivities {
    @ActivityMethod
    public ValidationResult validateUserInput(LLMProviderChatMessage userInput, ArrayList<LLMProviderChatMessage> history, String currentGoal);
}