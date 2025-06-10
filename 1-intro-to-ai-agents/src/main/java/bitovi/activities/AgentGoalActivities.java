package bitovi.activities;

import java.util.ArrayList;

import bitovi.activities.helpers.ToolPlannerResult;
import bitovi.activities.helpers.ValidationResult;
import bitovi.records.MessageRecord;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgentGoalActivities {
    @ActivityMethod
    public ValidationResult validateUserInput(MessageRecord userInput, ArrayList<MessageRecord> history, String currentGoal);

    @ActivityMethod
    public ValidationResult validatePrompt();

    @ActivityMethod
    public String generateInstructions(MessageRecord userInput, ArrayList<MessageRecord> history, String currentGoal);

    @ActivityMethod
    public ToolPlannerResult toolPlanner(String instructions, String prompt);
}