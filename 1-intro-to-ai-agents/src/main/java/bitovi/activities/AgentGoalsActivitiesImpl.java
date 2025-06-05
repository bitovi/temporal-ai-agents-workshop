package bitovi.activities;

import java.util.ArrayList;

import bitovi.activities.helpers.ValidationResult;
import bitovi.providers.LLMProviderChatMessage;

public class AgentGoalsActivitiesImpl implements AgentGoalActivities {

    @Override
    public ValidationResult validateUserInput(LLMProviderChatMessage userInput,
            ArrayList<LLMProviderChatMessage> history,
            String currentGoal) {
        return new ValidationResult(false, "The function is not implemented yet.");
    }
}
