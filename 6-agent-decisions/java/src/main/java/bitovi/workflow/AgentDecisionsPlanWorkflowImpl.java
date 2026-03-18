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
import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.PlanWorkflowResult;
import bitovi.workflow.types.UsageMetadata;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

public class AgentDecisionsPlanWorkflowImpl implements AgentDecisionsPlanWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofMinutes(5))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	@Override
	public PlanWorkflowResult execute(MessagePayload msg) {
		List<String> context = new ArrayList<>();
		List<UsageMetadata> usage = new ArrayList<>();

		// Add initial message to context
		context.add("User:" + msg.message());

		// Generate the Plan
		PlanResponse plan = activities.planActivity(context);

		if (plan.usageMetadata() != null) {
			usage.add(plan.usageMetadata());
		}

		HashMap<Integer, PlanStep> stepMap = new HashMap<>();
		HashMap<Integer, PlanStepResult> resultMap = new HashMap<>();
		List<Integer> failed = new ArrayList<>();

		// Build the initial stepMap
		for (PlanStep step : plan.steps()) {
			stepMap.put(step.id(), step);
		}

		// Main event loop
		while (true) {
			List<PlanStep> pending = new ArrayList<>();

			// For each step, check if dependencies are met
			for (PlanStep step : stepMap.values()) {
				if (resultMap.containsKey(step.id()) || failed.contains(step.id())) {
					continue; // Skip if already executed or failed
				}

				boolean dependenciesMet = true;
				List<PlanStepResult> dependencyResults = new ArrayList<>();
				for (Integer depId : step.dependsOn()) {
					if (resultMap.containsKey(depId)) {
						dependencyResults.add(resultMap.get(depId));
					} else if (failed.contains(depId)) {
						failed.add(step.id());
						dependenciesMet = false;
						break;
					} else {
						dependenciesMet = false;
						break;
					}
				}

				if (dependenciesMet) {
					pending.add(step);
				}
			}

			// If no pending steps, break the loop
			if (pending.isEmpty()) {
				break;
			}

			// Execute the pending steps in parallel
			List<Promise<PlanStepResult>> promises = new ArrayList<>();
			for (PlanStep step : pending) {
				List<PlanStepResult> deps = new ArrayList<>();
				for (Integer depId : step.dependsOn()) {
					PlanStepResult temp = resultMap.get(depId);
					if (temp == null) {
						throw new RuntimeException("Dependency result not found for step " + step.id() + ": " + depId);

					}
					deps.add(temp);
				}
				promises.add(Async.function(activities::executePlanStep, step, deps));
			}

			Promise.allOf(promises).get();

			// Collect results
			List<PlanStepResult> results = promises.stream()
					.map(Promise::get)
					.collect(Collectors.toList());

			// Update resultMap and failed lists
			for (PlanStepResult result : results) {
				if (result == null) {
					return new PlanWorkflowResult("Plan execution failed", new UsageMetadata(0, 0, 0, 0));
				}

				if (result.error()) {
					failed.add(result.id());
				}

				if (!result.error()) {
					context.add("Step " + result.id() + ", Tool: " + result.tool_name() + ", Input: "
							+ new JSONObject(result.tool_input().parameters()).toString() + ", Result: "
							+ result.result());
					resultMap.put(result.id(), result);
				}
			}

			// If there are failed steps, we're done.
			// TODO: Eventually we should re-plan instead of just failing.
			if (!failed.isEmpty()) {
				UsageMetadata failedUsage = compileUsageMetadata(usage);
				return new PlanWorkflowResult("Plan execution failed for steps: " + failed.toString(),
						failedUsage);
			}
		}

		// Generate final response
		FinalResponse finalResponse = activities.executeResponse(context);
		usage.add(finalResponse.usageMetadata());

		UsageMetadata finalUsage = compileUsageMetadata(usage);
		return new PlanWorkflowResult(finalResponse.response(), finalUsage);
	}

	UsageMetadata compileUsageMetadata(List<UsageMetadata> usage) {
		return usage.stream()
				.reduce(new UsageMetadata(0, 0, 0, 0),
						(acc, curr) -> new UsageMetadata(
								acc.inputTokens() + curr.inputTokens(),
								acc.outputTokens() + curr.outputTokens(),
								acc.reasoningTokens() + curr.reasoningTokens(),
								acc.totalTokens() + curr.totalTokens()));
	}
}
