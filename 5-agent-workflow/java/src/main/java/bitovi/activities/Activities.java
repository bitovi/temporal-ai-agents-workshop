package bitovi.activities;

import java.util.List;

import bitovi.common.AWS;
import bitovi.common.AWS.ModelToolCall;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface Activities {

	@ActivityMethod
	AWS.ModelResponse thought(List<AWS.ChatMessage> history) throws ApplicationFailure;

	@ActivityMethod
	AWS.ModelResponse action(List<AWS.ChatMessage> history) throws ApplicationFailure;

	@ActivityMethod
	AWS.ModelResponse observation(List<AWS.ChatMessage> history) throws ApplicationFailure;

	@ActivityMethod
	AWS.ToolResult executeTool(ModelToolCall toolCall) throws ApplicationFailure;
}
