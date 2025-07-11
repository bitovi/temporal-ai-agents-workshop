package bitovi.activities;

import java.util.List;

import bitovi.common.AWS;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface Activities {

	@ActivityMethod
	AWS.ModelResponse prompt(List<AWS.ChatMessage> history) throws ApplicationFailure;

	@ActivityMethod
	String executeTool(String toolName, String toolInputs) throws ApplicationFailure;
}
