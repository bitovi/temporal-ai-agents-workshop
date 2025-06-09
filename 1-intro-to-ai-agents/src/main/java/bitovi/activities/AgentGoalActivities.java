package bitovi.activities;

import java.util.ArrayList;

import bitovi.activities.helpers.ToolPlannerResult;
import bitovi.activities.helpers.ValidationResult;
import bitovi.providers.LLMProviderChatMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgentGoalActivities {
    @ActivityMethod
    public ValidationResult validateUserInput(LLMProviderChatMessage userInput, ArrayList<LLMProviderChatMessage> history, String currentGoal);

    @ActivityMethod
    public ValidationResult validatePrompt();

    @ActivityMethod
    public String generateInstructions(LLMProviderChatMessage userInput, ArrayList<LLMProviderChatMessage> history, String currentGoal);

    @ActivityMethod
    public ToolPlannerResult toolPlanner(String instructions, String prompt);
}