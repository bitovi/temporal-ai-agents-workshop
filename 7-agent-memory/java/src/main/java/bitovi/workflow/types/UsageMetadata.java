package bitovi.workflow.types;

public record UsageMetadata(
		int inputTokens,
		int outputTokens,
		int totalTokens) {

	public UsageMetadata add(UsageMetadata other) {
		return new UsageMetadata(
				this.inputTokens + other.inputTokens,
				this.outputTokens + other.outputTokens,
				this.totalTokens + other.totalTokens);
	}

	public static UsageMetadata empty() {
		return new UsageMetadata(0, 0, 0);
	}
}
