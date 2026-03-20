package bitovi.common.local;

import java.util.List;

import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.workflow.MemoryExtractionWorkflow;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.MemoryExtractionEventInput;
import bitovi.workflow.types.MemoryExtractionWorkflowInput;
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
            List<MemoryStrategyType> strategyTypes) {
        System.out
                .println("retrieveMemoryRecords called with query: " + query + " and strategyTypes: " + strategyTypes);
        return new RetrieveMemoryRecordsResult(List.of());
    }
}
