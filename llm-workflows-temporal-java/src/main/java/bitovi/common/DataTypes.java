package bitovi.common;

import java.util.List;
import java.util.Map;

import bitovi.workflows.AgentGoal.AgentGoalTypes.AgentGoalConversationHistory;

public interface DataTypes {

    record ToolPlannerResult(
            boolean forceConfirm,
            String toolName,
            String nextStep,
            java.util.ArrayList<DataTypes.ToolCallArgs> args) {
        // This record class encapsulates the result of a tool planning operation.
        // It includes whether confirmation is forced, the name of the tool,
        // the next step to take, and a list of arguments for the tool.
    }

    record ToolCallArgs(String toolKey, String toolValue) {
        // This record class encapsulates the arguments for a tool call.
        // It includes the key and value for the tool.
    }

    record ToolCallPromptInput(
            String prompt,
            String instructions) {
        // This record class encapsulates the input for a tool call prompt.
        // It includes the prompt text and any instructions for the tool.
    }

    record MessageRecord(String role, String content) {
        // Record classes, which are a special kind of class, help to model plain data
        // aggregates with less ceremony than normal classes.
    }

    record ValidationResultRecord(boolean isValid, String message) {
        // Record classes, which are a special kind of class, help to model plain data
        // aggregates with less ceremony than normal classes.
    }

    record ValidationInputRecord(String userInput, AgentGoalConversationHistory history, Agent currentGoal) {
        // This record class encapsulates the input for validation.
        // It includes the user input message, the conversation history, and the current
        // goal.
    }

    record ToolDataRecord(String nextStep, String tool, Map<String, String> args, String response,
            boolean forceConfirm) {
        // This record class encapsulates the data related to a tool execution.
        // It includes the next step to take, the name of the tool, a map of arguments,
        // and the response from the tool.
    }

    record AnyRecord(String type, Object data) {
        // Because I come from TypeScript, I would like to have a dumb generic for any
        // type of data while prototyping
    }

    enum ConnectionType {
        STDIO, REMOTE_SSE
    }

    record AgentGoalRecord(String id, String categoryTag, String agentName, String agentFriendlyDescription,
            List<software.amazon.awssdk.services.bedrockruntime.model.Tool> tools, String description,
            String starterPrompt, String exampleConversationHistory,
            MCPServerDefinitionRecord mcpServerDefinition) {

    }

    record MCPServerDefinitionRecord(String name, String command, List<String> args, Map<String, String> envVars,
            ConnectionType connectionType, List<String> includedTools) {
        // This record class encapsulates the definition of an MCP server.
        // It includes the name, command, arguments, and environment variables for the
        // server.
    }

    record EnvLookupInputRecord(String show_confirm_env_var_name, boolean show_confirm_default) {

    }

    record EnvLookupOutputRecord(boolean showConfirm, boolean multiGoalMode) {

    }

    record MCPToolDefinition(String name, String description, List<String> args) {
        // This record class encapsulates the definition of an MCP tool.
        // It includes the name, description, and arguments for the tool.
    }

    record PromptSummaryRecord(String contextInstructions, List<MessageRecord> actualPrompt) {
        // This record class encapsulates the summary of a prompt.
        // It includes the context instructions and the actual prompt text.
    }
}
