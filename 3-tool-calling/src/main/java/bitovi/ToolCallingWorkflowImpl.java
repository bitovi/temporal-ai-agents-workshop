package bitovi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import bitovi.activities.Activities;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.AWS.ModelToolCall;
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
		history.add(new ChatMessage("assistant",
				"I'd be happy to check the weather in San Francisco for you, but I need a zip code to look up the weather information. San Francisco has multiple zip codes.\nCould you please provide a specific zip code for San Francisco that you'd like the weather for?"));
		history.add(new ChatMessage("user", "94102 please!"));

		while (true) {
			AWS.ModelResponse modelResponse = activities.prompt(history);
			if (modelResponse.response() != null) {
				// If the model responds with text, hopefully the final answer, we can return
				// it!
				return modelResponse.response();
			}

			if (modelResponse.toolCall() == null) {
				// We should never get here, we didnt get text or a tool call out of the model.
				// Just give up.
				return null;
			}

			// The model responded with a tool call, so we need to execute it.
			ModelToolCall modelToolCall = modelResponse.toolCall();
			String toolResponse = activities.executeTool(modelToolCall);
			history.add(new ChatMessage("assistant",
					"Tool: " + modelToolCall.toolName() + ", Inputs: " + modelToolCall.toolInputsDocument()
							+ ", Result: " + toolResponse));
		}
	}
}