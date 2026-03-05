package bitovi.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface MockMcpServer {
	@ActivityMethod
	String checkMockMcpServerConnection() throws ApplicationFailure;
}
