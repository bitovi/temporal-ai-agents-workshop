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
	ThoughtResponse thought(String query, List<String> context) throws ApplicationFailure;

	@ActivityMethod
	ActionResponse action(String name, Object inputs) throws ApplicationFailure;

	@ActivityMethod
	ObservationResponse observation(String query, List<String> context, String actionResult) throws ApplicationFailure;

	@ActivityMethod
	CompactResponse compact(String query, List<String> context) throws ApplicationFailure;
}
