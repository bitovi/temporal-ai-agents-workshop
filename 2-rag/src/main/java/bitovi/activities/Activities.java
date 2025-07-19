package bitovi.activities;

import java.util.ArrayList;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface Activities {
	@ActivityMethod
	void embed(String url) throws ApplicationFailure;

	@ActivityMethod
	ArrayList<String> search(String searchTerm) throws ApplicationFailure;
}
