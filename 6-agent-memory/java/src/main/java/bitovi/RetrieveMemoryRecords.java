package bitovi;


import bitovi.common.Config;
import bitovi.common.aws.AgentCoreMemory;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class RetrieveMemoryRecords {

	public static void main(String[] args) throws Exception {
		Config config = new Config();

		RetrieveMemoryRecordsResponse response = AgentCoreMemory.retrieveMemoryRecords("What do you remember about me?", MemoryStrategyType.USER_PREFERENCE);
		
		
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
