package bitovi.workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import bitovi.activities.Activities;
import bitovi.activities.types.ActionDetail;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.ThoughtResponse;
import bitovi.workflow.types.ContinueAsNewState;
import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.UsageMetadata;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class AgentDecisionsWorkflowImpl implements AgentDecisionsWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
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
		Workflow.getLogger(AgentDecisionsWorkflowImpl.class).info("Received message from: " + payload.name());
	}

	@Override
	public void requestExit() {
		userRequestedExit = true;
		Workflow.getLogger(AgentDecisionsWorkflowImpl.class).info("Exit requested");
	}

	@Override
	public void requestContinueAsNew() {
		userRequestedContinueAsNew = true;
		Workflow.getLogger(AgentDecisionsWorkflowImpl.class).info("ContinueAsNew requested");
	}

	@Override
	public String getAnswer() {
		return answer;
	}

	@Override
	public WorkflowResult execute(WorkflowInput input) {
		// Initialize state from input
		List<String> context = input.continueAsNew() != null ? input.continueAsNew().context() : new ArrayList<>();
		List<UsageMetadata> usage = input.continueAsNew() != null ? input.continueAsNew().usage() : new ArrayList<>();

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
						.reduce(new UsageMetadata(0, 0, 0, 0),
								(acc, curr) -> new UsageMetadata(
										acc.inputTokens() + curr.inputTokens(),
										acc.outputTokens() + curr.outputTokens(),
										acc.reasoningTokens() + curr.reasoningTokens(),
										acc.totalTokens() + curr.totalTokens()));
				return new WorkflowResult(finalUsage);
			}

			// Check if the user or temporal recommended continueAsNew
			boolean shouldContinueAsNew = (userRequestedContinueAsNew || Workflow.getInfo().isContinueAsNewSuggested());

			// Check if context token usage exceeds threshold
			Integer contextLength = activities.getTokenUsage(context);
			Workflow.getLogger(AgentDecisionsWorkflowImpl.class).info("Current context token usage: " + contextLength);

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
				// Create list of messages to persist
				List<PersistMessage> messagesToPersist = new ArrayList<>();

				for (MessagePayload msg : pendingMsgs) {
					// Add user message to context with XML-like formatting
					String userMessage = String.format("<user_message name=\"%s\" date=\"%s\">\n%s\n</user_message>",
							msg.name(), msg.date(), msg.message());
					context.add(userMessage);

					// Add to persist list
					messagesToPersist.add(new PersistMessage("user", msg.message(), msg.date(), msg.name()));
				}

				// Clear pending messages
				pendingMsgs.clear();

				// Persist the user messages
				activities.persistActivity(messagesToPersist);
			}

			// Start ReAct (Reasoning and Acting) Steps
			ThoughtResponse thoughtResponse = activities.thoughtActivity(context);

			// Track usage
			if (thoughtResponse.usage() != null) {
				usage.add(thoughtResponse.usage());
			}

			if (thoughtResponse.type().equals("answer")) {
				// Answer type - add to context and wait for next message
				String answerContext = String.format("<answer>\n%s\n</answer>", thoughtResponse.answer());
				context.add(answerContext);

				// Persist assistant message
				List<PersistMessage> assistantMessages = List.of(
						new PersistMessage("assistant", thoughtResponse.answer(), null, null));
				activities.persistActivity(assistantMessages);

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
				String thoughtContext = String.format("<thought>\n%s\n</thought>", thoughtResponse.thought());
				context.add(thoughtContext);

				// Serialize action input for context
				String actionInputJson;
				try {
					actionInputJson = new JSONObject(action.input().parameters()).toString();
				} catch (Exception e) {
					actionInputJson = "{}";
				}

				// Add action to context
				String actionContext = String.format(
						"<action><reason>\n%s\n</reason><name>%s</name><input>%s</input></action>",
						action.reason(), action.name(), actionInputJson);
				context.add(actionContext);

				// Execute the action
				String actionResult = activities.actionActivity(action.name(), action.input());

				// Get observation
				ObservationResponse observationResponse = activities.observationActivity(context, actionResult);

				// Track usage
				if (observationResponse.usage() != null) {
					usage.add(observationResponse.usage());
				}

				// Add observation to context
				String observationContext = String.format("<observation>\n%s\n</observation>",
						observationResponse.observations());
				context.add(observationContext);
			}
		}
	}
}
