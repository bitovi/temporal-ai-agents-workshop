package bitovi.activities;

import java.util.List;

import bitovi.activities.tools.WeatherTool;
import bitovi.common.AWS;
import bitovi.common.AWS.ModelToolCall;
import io.temporal.failure.ApplicationFailure;

public class ActivitiesImpl implements Activities {

	@Override
	public AWS.ModelResponse prompt(List<AWS.ChatMessage> history) throws ApplicationFailure {
		try {
			return AWS.bedrockConverse(history);
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailureWithCause("Error during Bedrock Converse",
					"BedrockConverseException", e, e.getMessage());
		}
	}

	@Override
	public String executeTool(ModelToolCall toolCall) throws ApplicationFailure {
		switch (toolCall.toolName()) {
			case "get_weather": {
				// Call the WeatherTool's execute method with the toolInputs
				System.out.print("Executing tool: " + toolCall.toolName() + " with inputs: "
						+ toolCall.toolInputs() + "\n");
				return WeatherTool.execute(toolCall.toolName(), toolCall.toolInputs());
			}

			// TODO_TOOLS: Implement your DefineYourOwnTool execution here.
			case "define_your_own_tool": {
				throw ApplicationFailure.newNonRetryableFailure("DefineYourOwnTool is not implemented yet.",
						"DefineYourOwnToolNotImplemented");
			}

			default: {
				throw ApplicationFailure.newNonRetryableFailure(toolCall.toolName(), "UnknownToolCall");
			}
		}

	}

}
