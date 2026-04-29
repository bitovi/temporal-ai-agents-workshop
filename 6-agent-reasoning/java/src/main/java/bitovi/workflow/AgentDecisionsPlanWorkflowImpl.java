package bitovi.workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

import bitovi.activities.Activities;
import bitovi.activities.types.FinalResponse;
import bitovi.activities.types.PlanResponse;
import bitovi.activities.types.PlanStep;
import bitovi.activities.types.PlanStepResult;
import bitovi.workflow.types.PlanContinueAsNewState;
import bitovi.workflow.types.PlanWorkflowInput;
import bitovi.workflow.types.PlanWorkflowResult;
import bitovi.workflow.types.UsageMetadata;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

public class AgentDecisionsPlanWorkflowImpl implements AgentDecisionsPlanWorkflow {
	private static final int MAX_REPLAN_ATTEMPTS = 5;

	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofMinutes(5))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	@Override
	public PlanWorkflowResult execute(PlanWorkflowInput input) {
		List<String> context = input.continueAsNew() != null ? input.continueAsNew().context() : new ArrayList<>();
		List<UsageMetadata> usage = input.continueAsNew() != null ? input.continueAsNew().usageMetadata()
				: new ArrayList<>();

		int replanAttempts = input.continueAsNew() != null ? input.continueAsNew().replanAttempts() : 0;

		if (input.continueAsNew() == null) {
			context.add("User: " + input.message());
		} else {
			context.add("The previous plan failed to result in a successful outcome. This is attempt number "
					+ (replanAttempts + 1) + " of " + MAX_REPLAN_ATTEMPTS
					+ ". Use the existing context and failed step information to generate a new plan.");
		}

		PlanResponse plan = activities.planActivity(context);
		if (plan.usageMetadata() != null) {
			usage.add(plan.usageMetadata());
		}

		HashMap<Integer, PlanStep> stepMap = new HashMap<>();
		HashMap<Integer, PlanStepResult> resultMap = new HashMap<>();
		HashMap<Integer, PlanStepResult> failedResultMap = new HashMap<>();

		List<Integer> failed = new ArrayList<>();

		for (PlanStep step : plan.steps()) {
			stepMap.put(step.id(), step);
		}

		while (true) {
			// Cascade failures before checking what's pending
			propagateFailures(stepMap, resultMap, failed);

			List<PlanStep> pending = getPendingSteps(stepMap, resultMap, failed);

			if (pending.isEmpty()) {
				if (failed.isEmpty()) {
					break; // All steps completed successfully
				}

				// Some steps failed, return a failed result if max re-plan attempts reached
				if (replanAttempts >= MAX_REPLAN_ATTEMPTS) {
					return new PlanWorkflowResult(
							"Plan execution failed after " + replanAttempts + " re-plan attempts. Failed steps: "
									+ failed,
							compileUsageMetadata(usage));
				}

				// Add failure details to context so the re-planner knows what happened
				for (Integer failedId : failed) {
					PlanStepResult failedResult = failedResultMap.get(failedId);
					if (failedResult != null) {
						context.add("Failed Step " + failedId + ", Tool: " + failedResult.tool_name()
								+ ", Input: " + new JSONObject(failedResult.tool_input().parameters()).toString()
								+ ", Error: " + failedResult.result());
					} else {
						context.add("Failed Step " + failedId + ": cascaded failure from a failed dependency");
					}
				}

				// Continue as New to re-Plan based on the updated context
				PlanContinueAsNewState continueAsNewState = new PlanContinueAsNewState(replanAttempts + 1, usage,
						context);
				Workflow.continueAsNew(
						new PlanWorkflowInput(input.name(), input.message(), input.date(), continueAsNewState));
			}

			// Execute pending steps in parallel
			List<Promise<PlanStepResult>> promises = new ArrayList<>();
			for (PlanStep step : pending) {
				List<PlanStepResult> deps = step.dependsOn().stream()
						.map(resultMap::get)
						.collect(Collectors.toList());
				promises.add(Async.function(activities::executePlanStep, step, deps));
			}

			Promise.allOf(promises).get();

			for (Promise<PlanStepResult> promise : promises) {
				PlanStepResult result = promise.get();
				if (result.error()) {
					failed.add(result.id());
					failedResultMap.put(result.id(), result);
				} else {
					context.add("Step " + result.id() + ", Tool: " + result.tool_name() + ", Input: "
							+ new JSONObject(result.tool_input().parameters()).toString() + ", Result: "
							+ result.result());
					resultMap.put(result.id(), result);
				}
			}
		}

		FinalResponse finalResponse = activities.executeResponse(context);
		usage.add(finalResponse.usageMetadata());

		return new PlanWorkflowResult(finalResponse.response(), compileUsageMetadata(usage));
	}

	private void propagateFailures(HashMap<Integer, PlanStep> stepMap, HashMap<Integer, PlanStepResult> resultMap,
			List<Integer> failed) {
		boolean cascaded;
		do {
			cascaded = false;
			for (PlanStep step : stepMap.values()) {
				if (resultMap.containsKey(step.id()) || failed.contains(step.id())) {
					continue;
				}
				for (Integer depId : step.dependsOn()) {
					if (failed.contains(depId)) {
						failed.add(step.id());
						cascaded = true;
						break;
					}
				}
			}
		} while (cascaded);
	}

	private List<PlanStep> getPendingSteps(HashMap<Integer, PlanStep> stepMap,
			HashMap<Integer, PlanStepResult> resultMap,
			List<Integer> failed) {
		List<PlanStep> pending = new ArrayList<>();
		for (PlanStep step : stepMap.values()) {
			if (resultMap.containsKey(step.id()) || failed.contains(step.id())) {
				continue;
			}
			if (step.dependsOn().stream().allMatch(resultMap::containsKey)) {
				pending.add(step);
			}
		}
		return pending;
	}

	private UsageMetadata compileUsageMetadata(List<UsageMetadata> usage) {
		return usage.stream()
				.reduce(new UsageMetadata(0, 0, 0, 0),
						(acc, curr) -> new UsageMetadata(
								acc.inputTokens() + curr.inputTokens(),
								acc.outputTokens() + curr.outputTokens(),
								acc.reasoningTokens() + curr.reasoningTokens(),
								acc.totalTokens() + curr.totalTokens()));
	}
}
