package bitovi.activities;

import java.util.List;

import bitovi.activities.types.ActionInput;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.ThoughtResponse;
import bitovi.workflow.types.ContextEntry;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface Activities {
	@ActivityMethod
	ThoughtResponse thoughtActivity(List<ContextEntry> context) throws ApplicationFailure;

	@ActivityMethod
	String actionActivity(String toolName, ActionInput input) throws ApplicationFailure;

	@ActivityMethod
	ObservationResponse observationActivity(List<ContextEntry> context, String actionResult) throws ApplicationFailure;

	@ActivityMethod
	CompactResponse compactActivity(List<ContextEntry> context) throws ApplicationFailure;

	@ActivityMethod
	void persistActivity(List<PersistMessage> messages) throws ApplicationFailure;

	@ActivityMethod
	void persistMemoryActivity(List<ContextEntry> entries) throws ApplicationFailure;
}
