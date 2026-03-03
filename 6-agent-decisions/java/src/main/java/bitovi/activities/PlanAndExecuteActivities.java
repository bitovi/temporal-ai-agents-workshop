package bitovi.activities;

import java.util.List;

import bitovi.activities.types.ExecutableStep;
import bitovi.activities.types.PlanResponse;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import io.temporal.failure.ApplicationFailure;

@ActivityInterface
public interface PlanAndExecuteActivities {
	@ActivityMethod
	public PlanResponse planActivity(List<String> context) throws ApplicationFailure;

	@ActivityMethod
	public String executeActivity(ExecutableStep step) throws ApplicationFailure;
}
