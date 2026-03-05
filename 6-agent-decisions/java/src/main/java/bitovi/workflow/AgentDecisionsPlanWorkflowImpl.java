package bitovi.workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import bitovi.activities.Activities;
import bitovi.activities.types.PlanResponse;
import bitovi.activities.types.PlanStep;
import bitovi.activities.types.PlanStepResult;
import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.PlanWorkflowResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

public class AgentDecisionsPlanWorkflowImpl implements AgentDecisionsPlanWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	@Override
	public PlanWorkflowResult execute(MessagePayload msg) {

		List<String> context = new ArrayList<>();
		// List<UsageMetadata> usage = new ArrayList<>();

		// Add initial message to context
		context.add(msg.message());

		// Generate the Plan
		PlanResponse plan = activities.planActivity(context);

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
				if (result != null) {
					resultMap.put(result.id(), result);
				}
			}

			// If there are failed steps, we're done.
			// TODO: Eventually we should re-plan instead of just failing.
			if (!failed.isEmpty()) {
				return new PlanWorkflowResult("Plan execution failed", null);
			}
		}

		// Generate final response
		String finalResponse = activities.executeResponse(plan.steps(), new ArrayList<>(resultMap.values()));
		return new PlanWorkflowResult(finalResponse, null);
	}
}
