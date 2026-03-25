package bitovi.common.local;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import bitovi.activities.types.LabeledMemoryRecord;
import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.common.aws.BedrockEmbed;
import bitovi.workflow.MemoryExtractionWorkflow;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.MemoryExtractionEventInput;
import bitovi.workflow.types.MemoryExtractionWorkflowInput;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class LocalMemory {

        private static final Config config = new Config();

        private static final String USER_ID = config.getProperty("USER_ID");
        private static final String TEMPORAL_TASK_QUEUE = config.getProperty("TEMPORAL_TASK_QUEUE");

        public static void createEvent(List<ContextEntry> entries, String sessionId) {
                System.out.println("createEvent called with " + entries.size() + " entries");

                WorkflowClient temporalClient = TemporalClient.getTemporalClient();
                String workflowId = "extract-memory-" + USER_ID;

                WorkflowOptions workflowOptions = WorkflowOptions
                                .newBuilder()
                                .setTaskQueue(TEMPORAL_TASK_QUEUE)
                                .setWorkflowId(workflowId)
                                .build();

                MemoryExtractionWorkflow workflow = temporalClient
                                .newWorkflowStub(MemoryExtractionWorkflow.class, workflowOptions);

                WorkflowStub.fromTyped(workflow).signalWithStart(
                                "event",
                                new Object[] { new MemoryExtractionEventInput(entries, sessionId) },
                                new Object[] { new MemoryExtractionWorkflowInput(USER_ID) });
        }

        public static RetrieveMemoryRecordsResult retrieveMemoryRecords(String query,
                        List<MemoryStrategyType> strategyTypes) throws InterruptedException, ExecutionException {
                System.out.println("retrieveMemoryRecords called with query: " + query + " and strategyTypes: "
                                + strategyTypes);

                List<LabeledMemoryRecord> memories = new ArrayList<>();
                if (query == null || query.isEmpty()) {
                        System.out.println("Query is null or empty, returning empty memory records result");
                        return new RetrieveMemoryRecordsResult(memories);
                }

                List<Float> vector = BedrockEmbed.calculateEmbedding(query);

                try {
                        // Retrieve semantic memory records based on the embedding vector
                        // Fetch up to 20 records with a score threshold of 0.75
                        SemanticHelper semanticHelper = new SemanticHelper();
                        List<String> semanticMemories = semanticHelper.getSemanticMemoryPayloads(vector, 20, 0.75f);
                        for (String semanticMemory : semanticMemories) {
                                memories.add(new LabeledMemoryRecord(MemoryStrategyType.SEMANTIC, semanticMemory));
                        }
                } catch (Exception e) {
                        System.out.println("Error retrieving semantic memory records: " + e.getMessage());
                }

                try {
                        // Retrieve user preference memory records
                        // We should always just fetch all user preference memory records
                        UserPreferenceHelper userPreferenceHelper = new UserPreferenceHelper();
                        List<String> userPrefs = userPreferenceHelper.getUserPreferenceMemoryPayloads();

                        for (String userPref : userPrefs) {
                                memories.add(new LabeledMemoryRecord(MemoryStrategyType.USER_PREFERENCE, userPref));
                        }

                } catch (InterruptedException | ExecutionException e) {
                        System.out.println("Error retrieving user preference memory records: " + e.getMessage());
                }

                return new RetrieveMemoryRecordsResult(memories);
        }
}
