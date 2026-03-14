package bitovi.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface SupportAgentServer {
	@ActivityMethod
	String checkSupportAgentServerConnection() throws ApplicationFailure;
}
