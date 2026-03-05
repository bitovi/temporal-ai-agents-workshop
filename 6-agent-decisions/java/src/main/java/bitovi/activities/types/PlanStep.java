package bitovi.activities.types;

import java.util.List;

/**
 * Represents a step in a plan, including its ID, tool name, input, result type,
 * and dependencies.
 */
public record PlanStep(
        Integer id, String tool_name, ActionInput tool_input,
        List<Integer> dependsOn) {
}