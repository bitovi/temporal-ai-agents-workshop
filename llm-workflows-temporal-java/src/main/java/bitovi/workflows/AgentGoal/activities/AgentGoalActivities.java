package bitovi.workflows.AgentGoal.activities;

import java.util.ArrayList;

import bitovi.common.DataTypes;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgentGoalActivities {
    @ActivityMethod
    public DataTypes.ValidationResultRecord validateUserInput(DataTypes.MessageRecord userInput,
            ArrayList<DataTypes.MessageRecord> history,
            String currentGoal);

    @ActivityMethod
    public DataTypes.ValidationResultRecord validatePrompt();

    @ActivityMethod
    public String generateInstructions(DataTypes.MessageRecord userInput, ArrayList<DataTypes.MessageRecord> history,
            String currentGoal);

    @ActivityMethod
    public DataTypes.ToolPlannerResult toolPlanner(String instructions, String prompt);
}