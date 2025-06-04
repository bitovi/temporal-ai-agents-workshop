package bitovi.activities;

import java.util.ArrayList;

import bitovi.activities.helpers.ValidationResult;
import bitovi.workflows.AgentGoal.helpers.ChatMessage;

public class AgentGoalsActivitiesImpl implements AgentGoalActivities {

    @Override
    public ValidationResult validateUserInput(ChatMessage userInput, ArrayList<ChatMessage> history,
            String currentGoal) {
        return new ValidationResult(false, "The function is not implemented yet.");
    }
}
