package bitovi;

public record UsageMetadata(
	int inputTokens,
	int outputTokens,
	int totalTokens
) {
}
