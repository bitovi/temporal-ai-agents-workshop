package bitovi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import bitovi.activities.Activities;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;

public class ToolCallingWorkflowImpl implements ToolCallingWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	@Override
	public String execute() {
		List<ChatMessage> history = new ArrayList<>();

		history.add(new ChatMessage("user", "What is the weather in San Francisco?"));

		while (true) {
			AWS.ModelResponse modelResponse = activities.prompt(history);
			if (modelResponse.response() != null) {
				// If the model responds with text, hopefully the final answer, we can return
				// it!
				return modelResponse.response();
			}

			if (modelResponse.toolName() == null) {
				// We should never get here, we didnt get text or a tool call out of the mode.
				// Just give up.
				return null;
			}

			// The model responded with a tool call, so we need to execute it.
			String toolResponse = activities.executeTool(modelResponse.toolName(), modelResponse.toolInputs());
			history.add(new ChatMessage("assistant",
					"Tool: " + modelResponse.toolName() + ", Inputs: " + modelResponse.toolInputs().toString()
							+ ", Result: " + toolResponse));
		}
	}
}