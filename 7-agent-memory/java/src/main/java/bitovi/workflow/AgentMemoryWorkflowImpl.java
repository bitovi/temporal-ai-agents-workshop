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
			.setStartToCloseTimeout(Duration.ofMinutes(5))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	private static final int COMPACTION_CONTEXT_TOKEN_THRESHOLD = 100000;

	private static String answer = null;

	// Signal state
	private final List<MessagePayload> pendingMsgs = new ArrayList<>();
	private boolean userRequestedExit = false;
	private boolean userRequestedContinueAsNew = false;

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
	public void requestContinueAsNew() {
		userRequestedContinueAsNew = true;
		Workflow.getLogger(AgentMemoryWorkflowImpl.class).info("Compaction requested");
	}

	@Override
	public String getAnswer() {
		return answer;
	}

	@Override
	public WorkflowResult execute(WorkflowInput input) {
		// Initialize state from input
		List<ContextEntry> context = input.continueAsNew() != null ? input.continueAsNew().context()
				: new ArrayList<>();
		List<UsageMetadata> usage = input.continueAsNew() != null ? input.continueAsNew().usage() : new ArrayList<>();

		List<ContextEntry> persist = new ArrayList<>();

		// If continuing as new, restore pending messages
		if (input.continueAsNew() != null && input.continueAsNew().pending() != null) {
			pendingMsgs.addAll(input.continueAsNew().pending());
		}

		// Wait for first message, exit, or compaction request
		Workflow.await(() -> !pendingMsgs.isEmpty() || userRequestedExit || userRequestedContinueAsNew);

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
										acc.totalTokens() + curr.totalTokens()));
				return new WorkflowResult(finalUsage);
			}

			// Check if the user or temporal recommended continueAsNew
			boolean shouldContinueAsNew = (userRequestedContinueAsNew || Workflow.getInfo().isContinueAsNewSuggested());

			// Check if context token usage exceeds threshold
			Integer contextLength = activities.getTokenUsage(context);
			Workflow.getLogger(AgentMemoryWorkflowImpl.class).info("Current context token usage: " + contextLength);

			if (shouldContinueAsNew || (contextLength != null && contextLength > COMPACTION_CONTEXT_TOKEN_THRESHOLD)) {
				CompactResponse compactResponse = activities.compactActivity(context);

				// Track usage
				if (compactResponse.usage() != null) {
					usage.add(compactResponse.usage());
				}

				// Create continue-as-new state
				ContinueAsNewState continueAsNewState = new ContinueAsNewState(
						compactResponse.context(),
						usage,
						new ArrayList<>(pendingMsgs));

				// Continue as new
				Workflow.continueAsNew(new WorkflowInput(continueAsNewState));
			}

			// Process all pending messages
			if (!pendingMsgs.isEmpty()) {

				for (MessagePayload msg : pendingMsgs) {
					// Add user message to context as structured entry
					ContextEntry userEntry = new ContextEntry(
							Instant.now(),
							Role.USER,
							msg.message(),
							ContextEntryType.USER_MESSAGE,
							null,
							null,
							null);
					context.add(userEntry);
					persist.add(userEntry);
				}

				// Clear pending messages
				pendingMsgs.clear();
			}

			// Start ReAct (Reasoning and Acting) Steps
			// Retrieve Long Term Memories
			String query = context.stream()
					.map(ContextEntry::toXMLString)
					.collect(Collectors.joining("\n"));
			// TODO_MEMORY: Uncomment MemoryStrategyType.EPISODIC and SUMMARIZATION to
			// enable richer memory context (Part D)
			List<MemoryStrategyType> memoryStrategies = List.of(
					// MemoryStrategyType.EPISODIC,
					MemoryStrategyType.USER_PREFERENCE,
					MemoryStrategyType.SEMANTIC
			// MemoryStrategyType.SUMMARIZATION
			);
			RetrieveMemoryRecordsResult retrieveResult = activities.retrieveMemoryRecordsActivity(query,
					memoryStrategies);
			List<String> memoryRecords = retrieveResult.memoryRecords();

			// Get thought from AI based on context and retrieved memories
			ThoughtResponse thoughtResponse = activities.thoughtActivity(context, memoryRecords);

			// Track usage
			if (thoughtResponse.usage() != null) {
				usage.add(thoughtResponse.usage());
			}

			// Check response type
			if (thoughtResponse.type().equals("answer")) {
				// Answer type - add to context and wait for next message
				ContextEntry answerEntry = new ContextEntry(
						Instant.now(),
						Role.ASSISTANT,
						thoughtResponse.answer(),
						ContextEntryType.ANSWER,
						null,
						null,
						null);
				context.add(answerEntry);
				persist.add(answerEntry);

				// Batch persist: collect entries from most recent USER_MESSAGE to ANSWER
				activities.persistMemoryActivity(persist);

				// Once this has been persisted, we can clear the persist buffer
				persist.clear();

				// Store the most recent answer in a static variable to be retrieved by the
				// client after exit
				answer = thoughtResponse.answer();

				// Once the agent has generated an answer, we wait for the next user message or
				// other signal request before continuing
				Workflow.await(() -> !pendingMsgs.isEmpty() || userRequestedExit || userRequestedContinueAsNew);
			}

			if (thoughtResponse.type().equals("action")) {
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
						null);
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
						actionInputJson);
				context.add(actionEntry);

				// Execute the action
				String actionResult = activities.actionActivity(action.name(), action.input());

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
						null);

				context.add(observationEntry);
			}
		}
	}
}
