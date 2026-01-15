package bitovi.activities.DTO;

import java.util.List;

import bitovi.UsageMetadata;

public record CompactResponse(
	List<String> context,
	UsageMetadata usage
) {
}
