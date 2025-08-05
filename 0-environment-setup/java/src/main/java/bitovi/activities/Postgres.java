package bitovi.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface Postgres {

	@ActivityMethod
	void checkPostgresConnection() throws ApplicationFailure;
}