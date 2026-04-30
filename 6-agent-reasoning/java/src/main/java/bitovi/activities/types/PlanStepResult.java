package bitovi.activities.types;

public record PlanStepResult(
        Integer id, String tool_name, ActionInput tool_input, String result, Boolean error) {
}