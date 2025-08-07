package bitovi.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface S3 {

	@ActivityMethod
	String checkS3Connection() throws ApplicationFailure;
}