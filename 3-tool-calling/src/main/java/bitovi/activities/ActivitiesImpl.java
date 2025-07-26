package bitovi.activities;

import java.util.List;

import bitovi.activities.tools.WeatherTool;
import bitovi.common.AWS;
import bitovi.common.AWS.ModelToolCall;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.core.document.Document;

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
				String toolInputsDocument = toolCall.toolInputsDocument();
				System.out.print("Executing tool: " + toolCall.toolName() + " with inputs: "
						+ toolInputsDocument + "\n");
				Document toolInputsDoc = Document.fromString(toolInputsDocument);
				return WeatherTool.execute(toolInputsDoc);
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
