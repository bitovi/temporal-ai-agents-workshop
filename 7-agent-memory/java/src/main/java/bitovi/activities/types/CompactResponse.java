package bitovi.activities.types;

import java.util.List;

import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.UsageMetadata;

public record CompactResponse(
	List<ContextEntry> context,
	UsageMetadata usage
) {
}
