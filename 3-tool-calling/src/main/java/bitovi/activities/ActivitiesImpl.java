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
		return AWS.bedrockConverse(history);
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

			default: {
				throw ApplicationFailure.newNonRetryableFailure(toolCall.toolName(), "UnknownToolCall");
			}
		}

	}

}
