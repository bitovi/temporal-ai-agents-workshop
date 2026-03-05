package bitovi.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface AgentChatServer {
	@ActivityMethod
	String checkAgentChatServerConnection() throws ApplicationFailure;
}
