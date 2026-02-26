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
import bitovi.workflow.types.ReactStep;
import bitovi.workflow.types.UsageMetadata;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class AgentToAgentWorkflowImpl implements AgentToAgentWorkflow {
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
		Workflow.getLogger(AgentToAgentWorkflowImpl.class).info("Received message from: " + payload.name());
	}

	@Override
	public void requestExit() {
		userRequestedExit = true;
		Workflow.getLogger(AgentToAgentWorkflowImpl.class).info("Exit requested");
	}

	@Override
	public void requestCompaction() {
		userRequestedCompaction = true;
		Workflow.getLogger(AgentToAgentWorkflowImpl.class).info("Compaction requested");
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

				// Persist the user messages
				activities.persistActivity(messagesToPersist);
				
				// Clear pending messages
				pendingMsgs.clear();

				// Progress to thinking step to process new messages
				reactStep = ReactStep.THINKING;
			}


			// Start ReAct (Reasoning and Acting) Steps
			if (reactStep == ReactStep.THINKING) {
				// Get thought from AI
				ThoughtResponse thoughtResponse = activities.thoughtActivity(context);
				
				// Track usage
				if (thoughtResponse.usage() != null) {
					usage.add(thoughtResponse.usage());
				}

				// Check response type
				if ("answer".equals(thoughtResponse.type())) {
					// Answer type - add to context and wait for next message
					String answerContext = String.format("<answer>\n%s\n</answer>", thoughtResponse.answer());
					context.add(answerContext);
					
					// Persist assistant message
					List<PersistMessage> assistantMessages = List.of(
						new PersistMessage("assistant", thoughtResponse.answer(), null, null)
					);
					activities.persistActivity(assistantMessages);

					reactStep = ReactStep.IDLE;
					
				} else if ("action".equals(thoughtResponse.type())) {
					reactStep = ReactStep.ACTING;

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

					reactStep = ReactStep.OBSERVING;
					
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

					reactStep = ReactStep.THINKING;
					continue; // start ReAct (Reasoning and Acting) Loop again
				}
			}

			// Wait for next message, exit, or compaction request
			Workflow.await(() -> !pendingMsgs.isEmpty() || userRequestedExit || userRequestedCompaction);
		}
	}
}
