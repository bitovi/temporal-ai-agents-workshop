package bitovi.activities.types;

import bitovi.workflow.types.UsageMetadata;

public record ObservationResponse(
	String observations,
	UsageMetadata usage
) {
}
