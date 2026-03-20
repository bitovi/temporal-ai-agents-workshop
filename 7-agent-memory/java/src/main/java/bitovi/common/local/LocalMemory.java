package bitovi.common.local;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.common.aws.BedrockEmbed;
import bitovi.common.qdrant.VectorDatabaseClient;
import bitovi.workflow.MemoryExtractionWorkflow;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.MemoryExtractionEventInput;
import bitovi.workflow.types.MemoryExtractionWorkflowInput;
import bitovi.workflow.types.UsageMetadata;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class LocalMemory {

        private static final Config config = new Config();
        private static final String USER_ID = config.getProperty("USER_ID");
        private static final String TEMPORAL_TASK_QUEUE = config.getProperty("TEMPORAL_TASK_QUEUE");

        public static void createEvent(List<ContextEntry> entries) {
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
                                new Object[] { new MemoryExtractionEventInput(entries) },
                                new Object[] { new MemoryExtractionWorkflowInput(USER_ID) });
        }

        public static ListEventsResponse listEvents(String sessionId) {
                System.out.println("listEvents called with sessionId: " + sessionId);
                return ListEventsResponse.builder().build();
        }

        public static RetrieveMemoryRecordsResult retrieveMemoryRecords(String query,
                        List<MemoryStrategyType> strategyTypes) throws InterruptedException, ExecutionException {
                System.out.println("retrieveMemoryRecords called with query: " + query + " and strategyTypes: "
                                + strategyTypes);

                List<Float> vector = BedrockEmbed.calculateEmbedding(query);

                VectorDatabaseClient vdc = new VectorDatabaseClient(USER_ID);

                String[] results = vdc.searchVectorDatabase(vector, 20, 0.75f);

                return new RetrieveMemoryRecordsResult(List.of(results));
        }

        public static UsageMetadata extractUserPreferenceMemoriesImpl(List<ContextEntry> entries)
                        throws InterruptedException, ExecutionException {
                System.out.println("extractUserPreferenceMemoriesImpl called with entries: " + entries);

                // We should treat user preference memories differently from semantic memories.
                // These should be more like a 'fixed' set of key-value pairs that represent
                // facts about the user.
                
                // We should just include all of them, probably. Not semantic search them.
                return new UsageMetadata(0, 0, 0);
        }

        public static UsageMetadata extractSemanticMemoriesImpl(List<ContextEntry> entries)
                        throws InterruptedException, ExecutionException {
                System.out.println("extractSemanticMemoriesImpl called with entries: " + entries);

                // TODO: Actual LLM-based memory extraction logic.
                for (ContextEntry entry : entries) {
                        if (entry.content() != null && !entry.content().isEmpty()) {
                                System.out.println("Processing entry: " + entry);
                                System.out.println("  " + entry);

                                // For now, just embed the entries and store them in the vector database
                                List<Float> vector = BedrockEmbed.calculateEmbedding(entry.content());
                                VectorDatabaseClient vdc = new VectorDatabaseClient(USER_ID);
                                UUID uuid = java.util.UUID.randomUUID();
                                vdc.insertEmbedding(uuid, vector, entry.content(), "user_preference");
                        }
                }

                return new UsageMetadata(0, 0, 0);
        }

        public static void initializeMemoryStorage() throws InterruptedException, ExecutionException {
                System.out.println("initializeMemoryStorage called");
                VectorDatabaseClient vdc = new VectorDatabaseClient(USER_ID);
                vdc.createCollection();
        }
}
