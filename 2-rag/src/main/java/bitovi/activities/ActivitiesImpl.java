package bitovi.activities;

import io.temporal.failure.ApplicationFailure;

public class ActivitiesImpl implements Activities {
	@Override
	public String hello() throws ApplicationFailure {
		return "Hello";
	};
}
