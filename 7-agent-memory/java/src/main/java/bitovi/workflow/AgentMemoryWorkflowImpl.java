package bitovi.workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

import bitovi.activities.Activities;
import bitovi.activities.types.ActionDetail;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.activities.types.ThoughtResponse;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.ContextEntryType;
import bitovi.workflow.types.ContinueAsNewState;
import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.ReactStep;
import bitovi.workflow.types.UsageMetadata;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class AgentMemoryWorkflowImpl implements AgentMemoryWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	// Signal state
	private List<MessagePayload> pendingMsgs = new ArrayList<>();
	private boolean userRequestedExit = false;
	private boolean userRequestedCompaction = false;

	// React (Reasoning and Acting) Agent State
	private ReactStep reactStep = ReactStep.IDLE;

	@Override
	public void receiveMessage(MessagePayload payload) {
		pendingMsgs.add(payload);
		Workflow.getLogger(AgentMemoryWorkflowImpl.class).info("Received message from: " + payload.name());
	}

	@Override
	public void requestExit() {
		userRequestedExit = true;
		Workflow.getLogger(AgentMemoryWorkflowImpl.class).info("Exit requested");
	}

	@Override
	public void requestCompaction() {
		userRequestedCompaction = true;
		Workflow.getLogger(AgentMemoryWorkflowImpl.class).info("Compaction requested");
	}

	@Override
	public WorkflowResult execute(WorkflowInput input) {
		// Initialize state from input
		List<ContextEntry> context = input.continueAsNew() != null ? input.continueAsNew().context() : new ArrayList<>();
		List<UsageMetadata> usage = input.continueAsNew() != null ? input.continueAsNew().usage() : new ArrayList<>();
		
		// If continuing as new, restore pending messages
		if (input.continueAsNew() != null && input.continueAsNew().pending() != null) {
			pendingMsgs.addAll(input.continueAsNew().pending());
		}

		// Wait for first message, exit, or compaction request
		Workflow.await(() -> !pendingMsgs.isEmpty() || userRequestedExit || userRequestedCompaction);

		// Main event loop
		while (true) {
			// Check if exit requested
			if (userRequestedExit) {
				// Aggregate usage metrics and return
				UsageMetadata finalUsage = usage.stream()
					.reduce(new UsageMetadata(0, 0, 0),
						(acc, curr) -> new UsageMetadata(
							acc.inputTokens() + curr.inputTokens(),
							acc.outputTokens() + curr.outputTokens(),
							acc.totalTokens() + curr.totalTokens()
						));
				return new WorkflowResult(finalUsage);
			}

			// Check if compaction should happen
		 	boolean shouldCompact = (userRequestedCompaction || Workflow.getInfo().isContinueAsNewSuggested()) 
				&& pendingMsgs.isEmpty()
				&& !context.isEmpty()
				&& reactStep == ReactStep.IDLE;

			if (shouldCompact) {
				CompactResponse compactResponse = activities.compactActivity(context);
				
				// Track usage
				if (compactResponse.usage() != null) {
					usage.add(compactResponse.usage());
				}
				
				// Create continue-as-new state
				ContinueAsNewState continueAsNewState = new ContinueAsNewState(
					compactResponse.context(),
					usage,
					new ArrayList<>(pendingMsgs)
				);
				
				// Continue as new
				Workflow.continueAsNew(new WorkflowInput(continueAsNewState));
			}

			// Process all pending messages
			if (!pendingMsgs.isEmpty() && reactStep == ReactStep.IDLE) {
				
				for (MessagePayload msg : pendingMsgs) {
					// Add user message to context as structured entry
					ContextEntry userEntry = new ContextEntry(
						Instant.now(),
						Role.USER,
						msg.message(),
						ContextEntryType.USER_MESSAGE,
						null,
						null,
						null
					);
					context.add(userEntry);
				}
				
				// Clear pending messages
				pendingMsgs.clear();

				// Progress to thinking step to process new messages
				reactStep = ReactStep.THINKING;
			}


			// Start ReAct (Reasoning and Acting) Steps
			if (reactStep == ReactStep.THINKING) {
				// Retrieve Long Term Memories

				String query = context.stream()
					.map(ContextEntry::toXmlString)
					.collect(Collectors.joining("\n"));
				RetrieveMemoryRecordsResult retrieveResult = activities.retrieveMemoryRecordsActivity(query, MemoryStrategyType.USER_PREFERENCE);
				List<String> memoryRecords = retrieveResult.memoryRecords();
				

				// Get thought from AI
				ThoughtResponse thoughtResponse = activities.thoughtActivity(context, memoryRecords);
				
				// Track usage
				if (thoughtResponse.usage() != null) {
					usage.add(thoughtResponse.usage());
				}

				// Check response type
				if ("answer".equals(thoughtResponse.type())) {
					// Answer type - add to context and wait for next message
					ContextEntry answerEntry = new ContextEntry(
						Instant.now(),
						Role.ASSISTANT,
						thoughtResponse.answer(),
						ContextEntryType.ANSWER,
						null,
						null,
						null
					);
					context.add(answerEntry);
					
					// Batch persist: collect entries from most recent USER_MESSAGE to ANSWER
					List<ContextEntry> entriesToPersist = new ArrayList<>();
					boolean foundUserMessage = false;
					
					// Iterate backwards to find the most recent USER_MESSAGE
					for (int i = context.size() - 1; i >= 0; i--) {
						ContextEntry entry = context.get(i);
						entriesToPersist.add(0, entry);  // Add at beginning to maintain order
						
						if (entry.type() == ContextEntryType.USER_MESSAGE) {
							foundUserMessage = true;
							break;
						}
					}
					
					if (foundUserMessage) {
						activities.persistMemoryActivity(entriesToPersist);
					}

					reactStep = ReactStep.IDLE;
					
				} else if ("action".equals(thoughtResponse.type())) {
					reactStep = ReactStep.ACTING;

					// Action type - execute action and get observation
					ActionDetail action = thoughtResponse.action();
					
					// Add thought to context
					ContextEntry thoughtEntry = new ContextEntry(
						Instant.now(),
						Role.ASSISTANT,
						thoughtResponse.thought(),
						ContextEntryType.THOUGHT,
						null,
						null,
						null
					);
					context.add(thoughtEntry);
					
					// Serialize action input for context
					String actionInputJson;
					try {
						actionInputJson = new JSONObject(action.input().parameters()).toString();
					} catch (Exception e) {
						actionInputJson = "{}";
					}
					
					// Add action to context
					ContextEntry actionEntry = new ContextEntry(
						Instant.now(),
						Role.ASSISTANT,
						null,
						ContextEntryType.ACTION,
						action.reason(),
						action.name(),
						actionInputJson
					);
					context.add(actionEntry);
					
					// Execute the action
					String actionResult = activities.actionActivity(action.name(), action.input());

					reactStep = ReactStep.OBSERVING;
					
					// Get observation
					ObservationResponse observationResponse = activities.observationActivity(context, actionResult);
					
					// Track usage
					if (observationResponse.usage() != null) {
						usage.add(observationResponse.usage());
					}
					
					// Add observation to context
					ContextEntry observationEntry = new ContextEntry(
						Instant.now(),
						Role.ASSISTANT,
						observationResponse.observations(),
						ContextEntryType.OBSERVATION,
						null,
						null,
						null
					);
					context.add(observationEntry);

					reactStep = ReactStep.THINKING;
					continue; // start ReAct (Reasoning and Acting) Loop again
				}
			}

			// Wait for next message, exit, or compaction request
			Workflow.await(() -> !pendingMsgs.isEmpty() || userRequestedExit || userRequestedCompaction);
		}
	}
}
