package bitovi;


import java.util.List;

import bitovi.common.Config;
import bitovi.common.aws.AgentCoreMemory;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

/**
 * A handy utility for querying AWS Bedrock Agent Core Memory using semantic
 * search. Run this using the "RetrieveMemoryRecords" run configuration to retrieve
 * memory records most relevant to a given query, so you can verify what the agent
 * will recall in future conversations.
 *
 * Uncomment the desired {@link MemoryStrategyType} values to filter by strategy
 * before running.
 */
public class RetrieveMemoryRecords {

	public static void main(String[] args) throws Exception {
		Config config = new Config();

		var strategyTypes = List.of(
			// MemoryStrategyType.EPISODIC,
			MemoryStrategyType.USER_PREFERENCE,
			MemoryStrategyType.SEMANTIC
			// MemoryStrategyType.SUMMARIZATION
		);

		// Change the query below to use different semantic search terms and see how
		// the results change — memory records are ranked by relevance to the query.
		RetrieveMemoryRecordsResponse response = AgentCoreMemory.retrieveMemoryRecords("What do you remember about me?", strategyTypes);
		
		
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
