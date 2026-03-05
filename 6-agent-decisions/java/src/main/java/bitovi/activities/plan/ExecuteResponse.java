package bitovi.activities.plan;

import java.util.List;

import bitovi.activities.types.PlanStep;
import bitovi.activities.types.PlanStepResult;
import io.temporal.failure.ApplicationFailure;

public class ExecuteResponse {
  public static String execute(List<PlanStep> steps, List<PlanStepResult> results) throws ApplicationFailure {
    throw ApplicationFailure.newNonRetryableFailure("Unimplemented method 'executeResponse'", "UnimplementedMethod",
        steps);
  }
}
