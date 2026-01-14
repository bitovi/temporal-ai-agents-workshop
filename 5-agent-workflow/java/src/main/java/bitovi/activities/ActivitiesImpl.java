package bitovi.activities;

import java.util.List;

import bitovi.activities.DTO.ActionResponse;
import bitovi.activities.DTO.CompactResponse;
import bitovi.activities.DTO.ObservationResponse;
import bitovi.activities.DTO.ThoughtResponse;
import io.temporal.failure.ApplicationFailure;

public class ActivitiesImpl implements Activities {

	@Override
	public ThoughtResponse thought(String query, List<String> context) throws ApplicationFailure {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'thought'");
	}

	@Override
	public ActionResponse action(String name, Object inputs) throws ApplicationFailure {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'action'");
	}

	@Override
	public ObservationResponse observation(String query, List<String> context, String actionResult)
			throws ApplicationFailure {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'observation'");
	}

	@Override
	public CompactResponse compact(String query, List<String> context) throws ApplicationFailure {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'compact'");
	}

}
