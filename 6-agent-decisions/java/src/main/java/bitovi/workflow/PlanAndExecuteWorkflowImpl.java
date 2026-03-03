package bitovi.workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;

import bitovi.activities.Activities;
import bitovi.activities.PlanAndExecuteActivities;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.CompleteDependency;
import bitovi.activities.types.ExecutableStep;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.PlanResponse;
import bitovi.activities.types.PlanStep;
import bitovi.workflow.types.ContinueAsNewState;
import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.UsageMetadata;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class PlanAndExecuteWorkflowImpl implements PlanAndExecuteWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.build();

	private static final Logger logger = Workflow.getLogger(PlanAndExecuteWorkflowImpl.class);

	private final PlanAndExecuteActivities activities = Workflow.newActivityStub(PlanAndExecuteActivities.class,
			defaultActivityOptions);
	private final Activities common = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

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
			Integer contextLength = common.getTokenUsage(context);
			Workflow.getLogger(AgentDecisionsWorkflowImpl.class).info("Current context token usage: " + contextLength);

			if (shouldContinueAsNew || (contextLength != null && contextLength > COMPACTION_CONTEXT_TOKEN_THRESHOLD)) {
				CompactResponse compactResponse = common.compactActivity(context);

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
				common.persistActivity(messagesToPersist);
			}

			// Plan and Execute Steps
			PlanResponse planResponse = activities.planActivity(context);

			HashMap<String, PlanStep> taskMap = new HashMap<>();
			HashMap<String, String> taskResults = new HashMap<>();

			// Start the Executor Steps
			ArrayList<PlanStep> steps = planResponse.steps();
			steps.forEach(action -> {
				taskMap.put(action.id(), action);
			});

			// 1. Loop over all the tasks, collecting ones that have no dependencies or
			// whose dependencies have been met

			ArrayList<ExecutableStep> executableSteps = new ArrayList<>();

			steps.forEach(action -> {
				logger.info("Processing action: " + action.id());
				ArrayList<CompleteDependency> dependencies = new ArrayList<>();

				action.dependsOn().forEach(id -> {
					if (!taskResults.containsKey(id)) {
						logger.info("Dependency not met for action: " + action.id() + ", missing: " + id);
						return;
					}

					PlanStep dependency = taskMap.get(id);
					String dependencyResult = taskResults.get(id);

					logger.info("Dependency met for action: " + action.id() + ", dependency: " + id);
					dependencies.add(new CompleteDependency(id, dependency.result_type(), dependencyResult));
				});

				executableSteps.add(new ExecutableStep(action.id(), action.tool_name(),
						action.tool_input(), action.result_type(), dependencies));
			});

			// 2. Execute the tasks in parallel
			logger.info("Collected " + executableSteps.size() + " to execute this loop");
			executableSteps.forEach(step -> {
				// Execute the step and then store the result
				String result = activities.executeActivity(step);
				taskResults.put(step.id(), result);
			});

			// 3. Collect the results and update the context
		}
	}
}
