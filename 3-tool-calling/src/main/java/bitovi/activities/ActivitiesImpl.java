package bitovi.activities;

import java.util.List;

import bitovi.activities.tools.WeatherTool;
import bitovi.common.AWS;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.core.document.Document;

public class ActivitiesImpl implements Activities {

	@Override
	public AWS.ModelResponse prompt(List<AWS.ChatMessage> history) throws ApplicationFailure {
		return AWS.bedrockConverse(history);
	}

	@Override
	public String executeTool(String toolName, String toolInputs) throws ApplicationFailure {
		switch (toolName) {
			case "get_weather": {
				// Call the WeatherTool's execute method with the toolInputs
				Document toolInputsDoc = Document.fromString(toolInputs);
				return WeatherTool.execute(toolInputsDoc);
			}

			default: {
				throw ApplicationFailure.newNonRetryableFailure(toolName, "UnknownToolCall");
			}
		}

	}

}
