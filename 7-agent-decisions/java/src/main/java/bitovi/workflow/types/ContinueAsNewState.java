package bitovi.workflow.types;

import java.util.List;

public record ContinueAsNewState(
	List<String> context,
	List<UsageMetadata> usage,
	List<MessagePayload> pending
) {
}
