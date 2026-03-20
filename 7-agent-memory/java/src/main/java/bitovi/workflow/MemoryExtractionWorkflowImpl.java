package bitovi.workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import bitovi.activities.Activities;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.MemoryExtractionEventInput;
import bitovi.workflow.types.MemoryExtractionWorkflowInput;
import bitovi.workflow.types.UsageMetadata;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class MemoryExtractionWorkflowImpl implements MemoryExtractionWorkflow {

    private final ActivityOptions defaultActivityOptions = ActivityOptions
            .newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
            .build();

    private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

    private final List<ContextEntry> pending = new ArrayList<>();

    private UsageMetadata totalUsageMetadata = new UsageMetadata(0, 0, 0);

    private String userId;

    @Override
    public WorkflowResult execute(MemoryExtractionWorkflowInput input) {
        this.userId = input.userId();

        // Process the pending context entries

        while (!pending.isEmpty()) {
            List<ContextEntry> entriesToProcess = new ArrayList<>(pending);
            pending.clear();

            // Run extraction for the memory types
            UsageMetadata userPreferenceMetadata = activities.extractUserPreferenceMemories(userId, entriesToProcess);
            UsageMetadata semanticMetadata = activities.extractSemanticMemories(userId, entriesToProcess);

            totalUsageMetadata = totalUsageMetadata.add(userPreferenceMetadata).add(semanticMetadata);
        }

        // Implement the workflow logic here
        return new WorkflowResult(totalUsageMetadata);
    }

    @Override
    public void receiveMessage(MemoryExtractionEventInput event) {
        pending.addAll(event.entries());
    }
}