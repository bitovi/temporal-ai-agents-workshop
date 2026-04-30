package bitovi.activities.types;

import java.util.ArrayList;

/**
 * Represents a step that can be executed, including its ID, tool name, input,
 * result type, and completed dependencies.
 */
public record ExecutableStep(
        String id, String tool_name, ActionInput tool_input, String result_type,
        ArrayList<CompleteDependency> dependsOn) {
}
