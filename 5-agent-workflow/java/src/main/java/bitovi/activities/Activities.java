package bitovi.activities;

import java.util.List;

import bitovi.activities.DTO.CompactResponse;
import bitovi.activities.DTO.ObservationResponse;
import bitovi.activities.DTO.PersistMessage;
import bitovi.activities.DTO.ThoughtResponse;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface Activities {
	@ActivityMethod
	ThoughtResponse thoughtEntity(List<String> context) throws ApplicationFailure;

	@ActivityMethod
	String actionEntity(String toolName, Object input) throws ApplicationFailure;

	@ActivityMethod
	ObservationResponse observationEntity(List<String> context, String actionResult) throws ApplicationFailure;

	@ActivityMethod
	CompactResponse compactEntity(List<String> context) throws ApplicationFailure;

	@ActivityMethod
	void persistEntity(List<PersistMessage> messages) throws ApplicationFailure;
}
