package bitovi.activities;


import java.util.List;

import bitovi.activities.types.ExecutableStep;
import bitovi.activities.types.PlanResponse;
import io.temporal.failure.ApplicationFailure;


public class PlanAndExecuteActivitiesImpl implements PlanAndExecuteActivities {
   	@Override
	public PlanResponse planActivity(List<String> context) throws ApplicationFailure {
		throw new RuntimeException("Not implemented yet");
	}

	@Override
	public String executeActivity(ExecutableStep step) throws ApplicationFailure {
		throw new RuntimeException("Not implemented yet");
	}
}
