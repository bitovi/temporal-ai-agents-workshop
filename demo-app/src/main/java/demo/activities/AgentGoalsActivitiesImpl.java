package demo.activities;

import java.util.ArrayList;

import demo.activities.helpers.ValidationResult;
import demo.workflows.AgentGoal.helpers.ChatMessage;

public class AgentGoalsActivitiesImpl implements AgentGoalActivities {

    @Override
    public ValidationResult validateUserInput(ChatMessage userInput, ArrayList<ChatMessage> history,
            String currentGoal) {
        return new ValidationResult(false, "The function is not implemented yet.");
    }
}
