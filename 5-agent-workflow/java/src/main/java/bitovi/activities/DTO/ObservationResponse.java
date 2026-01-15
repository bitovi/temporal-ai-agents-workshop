package bitovi.activities.DTO;

import bitovi.UsageMetadata;

public record ObservationResponse(
	String observations,
	UsageMetadata usage
) {
}
