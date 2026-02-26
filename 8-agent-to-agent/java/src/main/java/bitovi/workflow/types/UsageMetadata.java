package bitovi.workflow.types;

public record UsageMetadata(
	int inputTokens,
	int outputTokens,
	int totalTokens
) {
}
