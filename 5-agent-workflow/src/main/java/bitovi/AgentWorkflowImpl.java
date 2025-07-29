package bitovi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import bitovi.activities.Activities;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.AWS.ModelToolCall;
import bitovi.common.AWS.ToolResult;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;

public class AgentWorkflowImpl implements AgentWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	@Override
	public String execute(String userQuestion) {
		List<ChatMessage> history = new ArrayList<>();
		history.add(new ChatMessage("user", userQuestion));

		while (true) {
			// Run the Thought step
			AWS.ModelResponse thoughtResponse = activities.thought(history);

			history.add(new ChatMessage("assistant", thoughtResponse.response()));

			// Run the Action step
			AWS.ModelResponse actionResponse = activities.action(history);
			if (actionResponse.response() != null) {
				return actionResponse.response();
			}

			if (actionResponse.toolCall() != null) {
				ModelToolCall modelToolCall = actionResponse.toolCall();
				ToolResult toolResponse = activities.executeTool(modelToolCall);

				if (toolResponse != null) {
					if (toolResponse.isFinalResult()) {
						// If the tool call indicates a final result, return it
						return toolResponse.output();
					}

					// Add the tool call result to the history
					history.add(new ChatMessage("assistant",
							"Tool: " + modelToolCall.toolName() + ", Inputs: " + modelToolCall.toolInputs()
									+ ", Result: " + toolResponse.output()));
				}
			}

			// Run the Observation step
			AWS.ModelResponse observationResponse = activities.observation(history);
			if (observationResponse != null) {
				if (observationResponse.toolCall() != null) {
					ModelToolCall modelToolCall = observationResponse.toolCall();
					ToolResult toolResponse = activities.executeTool(modelToolCall);

					if (toolResponse != null) {
						if (toolResponse.isFinalResult()) {
							// If the tool call indicates a final result, return it
							return toolResponse.output();
						}
					}
				}

				history.add(new ChatMessage("assistant", observationResponse.response()));
			}
		}
	}
}