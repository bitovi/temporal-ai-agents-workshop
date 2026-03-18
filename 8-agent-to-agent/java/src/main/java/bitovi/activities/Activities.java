package bitovi.activities;

import java.util.List;

import bitovi.activities.types.ActionInput;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.ThoughtResponse;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

/**
 * Activity interface for the ReAct agent workflow.
 *
 * Each method maps to one step in the reasoning cycle:
 *   thoughtActivity     — LLM decides: answer the user or invoke a tool
 *   actionActivity      — execute the chosen tool (including A2A calls)
 *   observationActivity — LLM distills raw tool output into concise context
 *   compactActivity     — summarize context when it grows too large
 *   persistActivity     — save messages for audit/history
 *   getTokenUsage       — estimate token count for compaction threshold
 */
@ActivityInterface
public interface Activities {
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
}
