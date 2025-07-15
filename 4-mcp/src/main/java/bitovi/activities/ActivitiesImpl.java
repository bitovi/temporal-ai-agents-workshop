package bitovi.activities;

import java.util.List;

import bitovi.common.AWS;
import bitovi.common.ModelContextProtocolClient;
import bitovi.common.AWS.ModelToolCall;
import io.temporal.failure.ApplicationFailure;

public class ActivitiesImpl implements Activities {

	private final ModelContextProtocolClient mcpIntegration;

	public ActivitiesImpl() {
		this.mcpIntegration = new ModelContextProtocolClient();
	}

	@Override
	public AWS.ModelResponse prompt(List<AWS.ChatMessage> history) throws ApplicationFailure {
		return AWS.bedrockConverse(history);
	}

	@Override
	public String executeTool(ModelToolCall toolCall) throws ApplicationFailure {
		try {
			String result = mcpIntegration.executeMCPTool(
					toolCall.toolName(),
					toolCall.toolInputs());
			return result;
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailureWithCause("Error executing tool",
					"ToolExecutionError", e, toolCall.toolName());
		}
	}
}
