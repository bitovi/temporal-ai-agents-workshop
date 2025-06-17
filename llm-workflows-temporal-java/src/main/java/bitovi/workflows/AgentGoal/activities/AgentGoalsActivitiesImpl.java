package bitovi.workflows.AgentGoal.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.common.Agent;
import bitovi.common.DataTypes;
import bitovi.common.DataTypes.EnvLookupInputRecord;
import bitovi.common.DataTypes.EnvLookupOutputRecord;
import bitovi.common.DataTypes.ListModelContextProtocolToolsResult;
import bitovi.common.DataTypes.MCPServerDefinitionRecord;
import bitovi.common.DataTypes.MessageRecord;
import bitovi.common.DataTypes.ToolDataRecord;
import bitovi.common.DataTypes.ToolPlannerResult;
import bitovi.common.DataTypes.ValidationInputRecord;
import bitovi.common.DataTypes.ValidationResultRecord;

public class AgentGoalsActivitiesImpl implements AgentGoalActivities {

    @Override
    public DataTypes.ValidationResultRecord validatePrompt() {
        return new DataTypes.ValidationResultRecord(true, "");
    }

    public String generateInstructions(DataTypes.MessageRecord userInput, ArrayList<DataTypes.MessageRecord> history,
            String currentGoal) {
        StringBuilder instructions = new StringBuilder();
        instructions.append("You are an AI agent that helps fill required arguments for the tools described below.");
        instructions.append("You must respond with valid JSON ONLY, using the schema provided in the instructions.");
        instructions.append("\n");
        instructions.append("=== Conversation History ===\n");
        instructions.append("This is the ongoing history to determine which tool and arguments to gather:\n");
        instructions.append("*BEGIN CONVERSATION HISTORY*\n");
        for (DataTypes.MessageRecord message : history) {
            instructions.append("\n");
            instructions.append(message.role());
            instructions.append(": ");
            instructions.append(message.content());
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
    public ValidationResultRecord validateUserInput(ValidationInputRecord input) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'validateUserInput'");
    }

    @Override
    public EnvLookupOutputRecord getWorkflowEnvSettings(EnvLookupInputRecord input) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getWorkflowEnvSettings'");
    }

    @Override
    public ListModelContextProtocolToolsResult listModelContextProtocolTools(
            MCPServerDefinitionRecord mcpServerDefinition, List<String> includeTools) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'listModelContextProtocolTools'");
    }

    @Override
    public String generateGenAIPrompt(MessageRecord userInput, ArrayList<MessageRecord> history,
            Agent currentGoal) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'generateGenAIPrompt'");
    }

    @Override
    public ToolPlannerResult toolPlanner(String instructions, List<MessageRecord> history) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'toolPlanner'");
    }

    @Override
    public AgentToolPlannerResult agentToolPlanner(String prompt, String contextInstructions) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'agentToolPlanner'");
    }

    @Override
    public Object handleMissingArgs(String currentTool, String args, ToolDataRecord toolData,
            ArrayList<MessageRecord> promptQueue) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleMissingArgs'");
    }

}
