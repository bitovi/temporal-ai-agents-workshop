package bitovi.activities;

import java.util.ArrayList;

import bitovi.activities.helpers.ValidationResult;
import bitovi.workflows.AgentGoal.helpers.ChatMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgentGoalActivities {
    @ActivityMethod
    public ValidationResult validateUserInput(ChatMessage userInput, ArrayList<ChatMessage> history, String currentGoal);
}