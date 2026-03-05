package bitovi.activities.plan;

import java.util.List;

import bitovi.activities.types.PlanStep;
import bitovi.activities.types.PlanStepResult;
import io.temporal.failure.ApplicationFailure;

public class ExecutePlanStep {
  public static PlanStepResult execute(PlanStep step, List<PlanStepResult> dependsOn) throws ApplicationFailure {
    throw ApplicationFailure.newNonRetryableFailure("Unimplemented method 'executePlanStep'", "UnimplementedMethod",
        step);
  }
}
