package bitovi.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface Activities {
	@ActivityMethod
	String promptLLM(String userQuestion, String agentResponse) throws ApplicationFailure;
}
