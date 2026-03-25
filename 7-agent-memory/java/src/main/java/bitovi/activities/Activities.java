package bitovi.activities;

import java.util.List;

import bitovi.activities.types.ActionInput;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.LabeledMemoryRecord;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.activities.types.ThoughtResponse;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.UsageMetadata;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

@ActivityInterface
public interface Activities {
	@ActivityMethod
	ThoughtResponse thoughtActivity(List<ContextEntry> context, List<LabeledMemoryRecord> memoryRecords)
			throws ApplicationFailure;

	@ActivityMethod
	String actionActivity(String toolName, ActionInput input) throws ApplicationFailure;

	@ActivityMethod
	ObservationResponse observationActivity(String thought, String actionName, String actionInputs, String actionResult)
			throws ApplicationFailure;

	@ActivityMethod
	CompactResponse compactActivity(List<ContextEntry> context) throws ApplicationFailure;

	@ActivityMethod
	void persistActivity(List<PersistMessage> messages) throws ApplicationFailure;

	@ActivityMethod
	void persistMemoryActivity(List<ContextEntry> entries, String sessionId) throws ApplicationFailure;

	@ActivityMethod
	Integer getTokenUsage(List<ContextEntry> context) throws ApplicationFailure;

	@ActivityMethod
	RetrieveMemoryRecordsResult retrieveMemoryRecordsActivity(String query, List<MemoryStrategyType> strategyTypes)
			throws ApplicationFailure;

	// Memory Extraction Workflow

	@ActivityMethod
	UsageMetadata extractUserPreferenceMemories(String userId, String sessionId, List<ContextEntry> entries)
			throws ApplicationFailure;

	@ActivityMethod
	UsageMetadata extractSemanticMemories(String userId, String sessionId, List<ContextEntry> entries)
			throws ApplicationFailure;
}
