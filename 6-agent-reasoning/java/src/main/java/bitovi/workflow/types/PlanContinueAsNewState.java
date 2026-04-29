package bitovi.workflow.types;

import java.util.List;

public record PlanContinueAsNewState(
		Integer replanAttempts,
		List<UsageMetadata> usageMetadata,
		List<String> context) {
}
