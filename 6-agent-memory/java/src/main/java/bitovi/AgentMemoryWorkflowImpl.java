package bitovi;

import java.time.Duration;

import bitovi.activities.Bedrock;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

public class AgentMemoryWorkflowImpl implements AgentMemoryWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.build();

	private final Bedrock bedrockActivities = Workflow.newActivityStub(Bedrock.class, defaultActivityOptions);

	@Override
	public String execute() {
		bedrockActivities.checkBedrockMemoryConnection();

		return "Success";
	}
}