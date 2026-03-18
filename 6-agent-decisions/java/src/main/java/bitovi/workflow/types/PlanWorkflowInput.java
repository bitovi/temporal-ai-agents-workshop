package bitovi.workflow.types;

public record PlanWorkflowInput(
	String name,
	String message,
	String date,
    PlanContinueAsNewState continueAsNew
) {
}
