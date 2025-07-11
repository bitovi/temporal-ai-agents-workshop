package bitovi.activities;

import java.util.List;

import bitovi.common.AWS;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.core.document.Document;

@ActivityInterface
public interface Activities {

	@ActivityMethod
	AWS.ModelResponse prompt(List<AWS.ChatMessage> history) throws ApplicationFailure;

	@ActivityMethod
	String executeTool(String toolName, Document toolInputs) throws ApplicationFailure;
}
