package bitovi;

import java.time.Duration;

import bitovi.activities.Activities;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

public class AgentWorkflowImpl implements AgentWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	@Override
	public String execute() {
		// List<String> context = input.context() != null ? input.context() : new ArrayList<>();
		// String query = input.query();

		// while (true) {
		// 	ThoughtResponse thoughtResponse = activities.thought(query, context);

		// 	if (thoughtResponse.isFinalAnswer()) {
		// 		return thoughtResponse.getAnswer();
		// 	}

		// 	context.add("<thought>\n" + thoughtResponse.getThought() + "\n</thought>");

		// 	ActionResponse actionResponse = activities.action(thoughtResponse.getAction().getName(),
		// 			thoughtResponse.getAction().getInputs());

		// 	ObservationResponse observationResponse = activities.observation(query, context,
		// 			actionResponse.getResult());
		// 	context.add("<observation>\n" + observationResponse.observation() + "\n</observation>");

		// 	if (Workflow.getInfo().isContinueAsNewSuggested()) {
		// 		CompactResponse compactResponse = activities.compact(query, context);
		// 		return Workflow.continueAsNew(new ActionWorkflowInput(query, compactResponse.context()));
		// 	}
		// }
		return "Success";
	}
}