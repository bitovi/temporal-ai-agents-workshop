package bitovi.activities.types;

import java.util.List;

import bitovi.workflow.types.UsageMetadata;

public record CompactResponse(
	List<String> context,
	UsageMetadata usage
) {
}
