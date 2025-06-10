package bitovi.activities;

import java.util.ArrayList;

import bitovi.activities.helpers.ToolPlannerResult;
import bitovi.activities.helpers.ValidationResult;
import bitovi.providers.LLMProviderChatMessage;
import bitovi.providers.OllamaProvider;

public class AgentGoalsActivitiesImpl implements AgentGoalActivities {

    @Override
    public ValidationResult validateUserInput(LLMProviderChatMessage userInput,
            ArrayList<LLMProviderChatMessage> history,
            String currentGoal) {
        return new ValidationResult(false, "The function is not implemented yet.");
    }

    @Override
    public ValidationResult validatePrompt() {
        return new ValidationResult(true, "");
    }

    @Override
    public String generateInstructions(LLMProviderChatMessage userInput, ArrayList<LLMProviderChatMessage> history,
            String currentGoal) {
        StringBuilder instructions = new StringBuilder();
        instructions.append("You are an AI agent that helps fill required arguments for the tools described below.");
        instructions.append("You must respond with valid JSON ONLY, using the schema provided in the instructions.");
        instructions.append("\n");
        instructions.append("=== Conversation History ===\n");
        instructions.append("This is the ongoing history to determine which tool and arguments to gather:\n");
        instructions.append("*BEGIN CONVERSATION HISTORY*\n");
        for (LLMProviderChatMessage message : history) {
            instructions.append("\n");
            instructions.append(message.getRole());
            instructions.append(": ");
            instructions.append(message.getContent());
        }
        instructions.append("*END CONVERSATION HISTORY*\n");

        instructions.append("REMINDER: You should use the conversation history to infer arguments for the tools.\n");
        instructions.append("=== Tools Definitions ===");

        instructions.append("There are {number} available tools:\n");
        // TODO: List all the tool names here
        instructions.append("Goal:");
        instructions.append(currentGoal);
        instructions.append("\n");
        instructions.append("Gather the necessary information for each tool in the sequence described above.");
        instructions.append("Only ask for arguments listed below. Do not add extra arguments.");
        // TODO: Add the tools definitions here.

        instructions.append("When all required args for a tool are known, you can propose next='confirm' to run it.\n");
        instructions.append("=== Instructions for JSON Generation ===\n");
        instructions.append("Your JSON format must be:\n");
        // TODO: Add the JSON Schema here.

        instructions.append("1) If any required argument is missing, set next='question' and ask the user.\n");
        instructions.append(
                "2) If all required arguments are known, set next='confirm' and specify the tool. The user will confirm before the tool is run.\n");
        // TODO: generate_toolchain_complete_guidance
        instructions.append(
                "3) If no more tools are needed (user_confirmed_tool_run has been run for all), set next='done' and tool=''.\n");
        instructions.append("4) response should be short and user-friendly.\n\n");

        instructions.append("Guardrails (always remember!)\n");
        instructions.append("1) If any required argument is missing, set next='question' and ask the user.\n");
        instructions.append("1) ALWAYS ask a question in your response if next='question'.\n");
        instructions.append("2) ALWAYS set next='confirm' if you have arguments\n ");
        instructions.append("And respond with \"let\'s proceed with <tool> (and any other useful info)\" \n ");
        instructions.append("DON'T set next='confirm' if you have a question to ask.\n");
        instructions.append("EXAMPLE: If you have a question to ask, set next='question' and ask the user.\n");
        instructions.append("3) You can carry over arguments from one tool to another.\n ");
        instructions.append(
                "EXAMPLE: If you asked for an account ID, then use the conversation history to infer that argument ");
        instructions.append("going forward.");
        instructions.append("4) If ListAgents in the conversation history is force_confirm='False', you MUST check ");
        instructions.append(
                "if the current tool contains userConfirmation. If it does, please ask the user to confirm details ");
        instructions.append("with the user. userConfirmation overrides force_confirm='False'.\n");
        instructions.append(
                "EXAMPLE: (force_confirm='False' AND userConfirmation exists on tool) Would you like me to <run tool> ");
        instructions.append("with the following details: <details>?\n");

        return instructions.toString();
    }

    @Override
    public ToolPlannerResult toolPlanner(String instructions, String prompt) {

        ArrayList<LLMProviderChatMessage> messages = new ArrayList<>();
        messages.add(new LLMProviderChatMessage("system", instructions));
        messages.add(
                new LLMProviderChatMessage("system", "The current date and time is " + java.time.LocalDateTime.now()));

        messages.add(new LLMProviderChatMessage("user", prompt));

        try {
            OllamaProvider llm = new OllamaProvider();
            LLMProviderChatMessage response = llm.chat(messages);
            String cleanedResponse = clean(response.getContent());
            ToolPlannerResult result = parse(cleanedResponse);

            if (result.nextStep == "confirm") {
                // Check if the args are valid
                // handleMissingArgs(result.args);
            }

            return result;

        } catch (Exception e) {
            return new ToolPlannerResult(false, "next", null, null);
        }

    }

    private String clean(String input) {
        String cleaned = input.replace("```json", "").replace("```", "").strip();
        return cleaned;
    }

    private ToolPlannerResult parse(String maybeJsonString) {
        System.out.println("Parsing JSON: " + maybeJsonString);
        return new ToolPlannerResult(false, "next", null, null);
    }

}
