package bitovi;

import java.time.Duration;

import bitovi.activities.Activities;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

public class EmbedWorkflowImpl implements EmbedWorkflow {
    private final ActivityOptions defaultActivityOptions = ActivityOptions
            .newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(120))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
            .build();

    private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

    @Override
    public void execute(String[] urls) {
        // For each of our S3 URLs, we will call the `embed` activity
        for (String url : urls) {
            activities.embed(url);
        }
    }
}