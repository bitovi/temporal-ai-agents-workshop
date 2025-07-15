package bitovi;

import java.time.Duration;

import bitovi.activities.Activities;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;

public class EmbedWorkflowImpl implements EmbedWorkflow {
    private final ActivityOptions defaultActivityOptions = ActivityOptions
            .newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(120))
            .build();

    private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

    @Override
    public void execute(String[] urls) {
        // For each URL, call the embed activity
        // TODO: Maybe talk about parallel execution here
        for (String url : urls) {
            activities.embed(url);
        }
    }
}