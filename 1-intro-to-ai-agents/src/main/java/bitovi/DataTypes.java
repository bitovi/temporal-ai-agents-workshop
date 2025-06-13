package bitovi;

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
}
