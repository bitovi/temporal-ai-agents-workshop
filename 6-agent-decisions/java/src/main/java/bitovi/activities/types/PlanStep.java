package bitovi.activities.types;

import java.util.ArrayList;

/**
 * Represents a step in a plan, including its ID, tool name, input, result type,
 * and dependencies.
 */
public record PlanStep(
        String id, String tool_name, ActionInput tool_input, String result_type, ArrayList<String> dependsOn) {
}