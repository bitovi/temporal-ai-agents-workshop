package bitovi.activities;

import java.util.List;

import bitovi.activities.types.ActionInput;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.FinalResponse;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.PlanResponse;
import bitovi.activities.types.PlanStep;
import bitovi.activities.types.PlanStepResult;
import bitovi.activities.types.ThoughtResponse;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface Activities {
	/**
	 * Reasoning and Acting Activities
	 */
	@ActivityMethod
	ThoughtResponse thoughtActivity(List<String> context) throws ApplicationFailure;

	@ActivityMethod
	String actionActivity(String toolName, ActionInput input) throws ApplicationFailure;

	@ActivityMethod
	ObservationResponse observationActivity(String thought, String actionName, String actionInputs, String actionResult) throws ApplicationFailure;

	@ActivityMethod
	CompactResponse compactActivity(List<String> context) throws ApplicationFailure;

	@ActivityMethod
	void persistActivity(List<PersistMessage> messages) throws ApplicationFailure;

	@ActivityMethod
	Integer getTokenUsage(List<String> context) throws ApplicationFailure;

	/**
	 * Planning and Executing Activities
	 */
	@ActivityMethod
	PlanResponse planActivity(List<String> context) throws ApplicationFailure;

	@ActivityMethod
	PlanStepResult executePlanStep(PlanStep step, List<PlanStepResult> dependsOn) throws ApplicationFailure;

	@ActivityMethod
	FinalResponse executeResponse(List<String> context) throws ApplicationFailure;
}
