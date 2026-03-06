# Plan and Execute Code

```java
List<String> context = new ArrayList<>();
context.add(formatUserMessageContext(msg));

PlanResponse plan = activities.planActivity(context);
PlanStatus status = buildPlanStatus(plan);

// Main event loop
while (true) {
    List<PlanStep> pending = filterStepsWithMetDependencies(plan.steps(), status.results(), status.failed());
    if (pending.isEmpty()) {
        break;
    }

    List<Promise<PlanStepResult>> promises = new ArrayList<>();
    for (PlanStep step : pending) {
        List<PlanStepResult> deps = collectDependencies(step, status.results());
        promises.add(Async.function(activities::executePlanStep, step, deps));
    }
    List<PlanStepResult> results = collectResults(promises);

    for (PlanStepResult result : results) {
        if (!result.error()) {
            context.add(formatPlanStepResultContext(result));
            status.results().put(result.id(), result);
        } else {
            status.failed().add(result.id());
        }
    }
}

return activities.executeResponse(context);
```
