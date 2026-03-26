package bitovi.activities.types;

import java.util.List;

import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public record RetrieveMemoryRecordsResult(
		List<LabeledMemoryRecord> memories) {

	public String[] filterByStrategy(MemoryStrategyType strategy) {
		return memories.stream()
				.filter(m -> m.strategy().equals(strategy))
				.map(LabeledMemoryRecord::payload)
				.toArray(String[]::new);
	}
}