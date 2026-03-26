package bitovi.common.local;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;

import bitovi.activities.types.LabeledMemoryRecord;
import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.common.aws.BedrockEmbed;
import bitovi.common.local.types.SemanticMemoryRecord;
import bitovi.common.local.types.UserPreferenceMemoryRecord;
import bitovi.workflow.MemoryExtractionWorkflow;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.MemoryExtractionEventInput;
import bitovi.workflow.types.MemoryExtractionWorkflowInput;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class LocalMemory {

        private static final Logger logger = org.slf4j.LoggerFactory.getLogger(LocalMemory.class);

        private static final Config config = new Config();

        private static final String USER_ID = config.getProperty("USER_ID");
        private static final String TEMPORAL_TASK_QUEUE = config.getProperty("TEMPORAL_TASK_QUEUE");

        public static void createEvent(List<ContextEntry> entries, String sessionId) {
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

                List<LabeledMemoryRecord> memories = new ArrayList<>();
                if (query == null || query.isEmpty()) {
                        logger.warn("Query is null or empty, returning empty memory records result");
                        return new RetrieveMemoryRecordsResult(memories);
                }

                List<Float> vector = BedrockEmbed.calculateEmbedding(query);

                try {
                        // Retrieve semantic memory records based on the embedding vector
                        // Fetch up to 20 records with a score threshold of 0.75
                        SemanticHelper semanticHelper = new SemanticHelper();
                        List<SemanticMemoryRecord> semanticMemories = semanticHelper.getSemanticMemoryPayloads(vector,
                                        20, 0.75f);
                        for (SemanticMemoryRecord semanticMemory : semanticMemories) {
                                memories.add(new LabeledMemoryRecord(MemoryStrategyType.SEMANTIC,
                                                semanticMemory.fact()));
                        }
                        semanticHelper.close();
                } catch (InterruptedException | ExecutionException e) {
                        logger.error("Error retrieving semantic memory records: " + e.getMessage());
                }

                try {
                        // Retrieve user preference memory records
                        // We should always just fetch all user preference memory records
                        UserPreferenceHelper userPreferenceHelper = new UserPreferenceHelper();
                        List<UserPreferenceMemoryRecord> userPrefs = userPreferenceHelper
                                        .getUserPreferenceMemoryPayloads();

                        for (UserPreferenceMemoryRecord userPref : userPrefs) {
                                memories.add(new LabeledMemoryRecord(MemoryStrategyType.USER_PREFERENCE,
                                                userPref.preference()));
                        }

                        userPreferenceHelper.close();

                } catch (InterruptedException | ExecutionException e) {
                        logger.error("Error retrieving user preference memory records: " + e.getMessage());
                }

                return new RetrieveMemoryRecordsResult(memories);
        }
}
