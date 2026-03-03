package bitovi;


import java.util.List;

import bitovi.common.Config;
import bitovi.common.aws.AgentCoreMemory;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

/**
 * A handy utility for browsing the distilled memory records stored in
 * AWS Bedrock Agent Core Memory. Run this using the "ListMemoryRecords" run
 * configuration to see what the agent has learned and retained about the user
 * across all sessions, grouped by strategy type.
 *
 * Uncomment the desired {@link MemoryStrategyType} values to filter by strategy
 * before running.
 */
public class ListMemoryRecords {

	public static void main(String[] args) throws Exception {
		Config config = new Config();

		var strategyTypes = List.of(
			// MemoryStrategyType.EPISODIC,
			MemoryStrategyType.USER_PREFERENCE
			// MemoryStrategyType.SEMANTIC,
			// MemoryStrategyType.SUMMARIZATION
		);

		ListMemoryRecordsResponse response = AgentCoreMemory.listMemoryRecords(strategyTypes);
		
		
		if (response.memoryRecordSummaries().isEmpty()) {
			System.out.println("Found 0 memory record(s)");
			return;
		}

		System.out.println(String.format("Found %s memory record(s)", response.memoryRecordSummaries().size()));

		for (MemoryRecordSummary memoryRecordSummary : response.memoryRecordSummaries()) {
			MemoryStrategyType memoryStrategyType = AgentCoreMemory.getMemoryStrategyType(memoryRecordSummary);
			MemoryContent memoryContent = memoryRecordSummary.content();
			if (memoryContent.type() != MemoryContent.Type.TEXT) { continue; }
			String text = memoryContent.text();

			System.out.println(String.format("\n[%s]: %s", memoryStrategyType, text));
		}
	}
}
