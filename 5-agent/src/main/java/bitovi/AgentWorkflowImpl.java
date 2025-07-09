package bitovi;

import java.time.Duration;

import bitovi.activities.Activities;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;

public class AgentWorkflowImpl implements AgentWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	@Override
	public String execute() {
		return activities.hello();
	}
}