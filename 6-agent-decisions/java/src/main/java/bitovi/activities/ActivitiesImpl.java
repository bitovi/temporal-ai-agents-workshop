package bitovi.activities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import bitovi.activities.types.ActionInput;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.ThoughtResponse;
import io.temporal.failure.ApplicationFailure;

public class ActivitiesImpl implements Activities {

	@Override
	public ThoughtResponse thoughtActivity(List<String> context) throws ApplicationFailure {
		String promptTemplate = loadPromptTemplate("/prompts/thought-prompt.txt");
		return Thought.execute(promptTemplate, context);
	}

	@Override
	public String actionActivity(String toolName, ActionInput input) throws ApplicationFailure {
		return Action.execute(toolName, input);
	}

	@Override
	public ObservationResponse observationActivity(List<String> context, String actionResult)
			throws ApplicationFailure {
		String promptTemplate = loadPromptTemplate("/prompts/observation-prompt.txt");
		return Observation.execute(promptTemplate, context, actionResult);
	}

	@Override
	public CompactResponse compactActivity(List<String> context) throws ApplicationFailure {
		String promptTemplate = loadPromptTemplate("/prompts/compact-prompt.txt");
		return Compact.execute(promptTemplate, context);
	}

	@Override
	public void persistActivity(List<PersistMessage> messages) throws ApplicationFailure {
		Persist.execute(messages);
	}

	/**
	 * Load a prompt template from resources.
	 * 
	 * @param resourcePath Path to the prompt template (e.g.,
	 *                     "/prompts/thought-prompt.txt")
	 * @return The prompt template as a string
	 */
	private String loadPromptTemplate(String resourcePath) {
		try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
			if (inputStream == null) {
				throw new RuntimeException("Prompt template not found: " + resourcePath);
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			return reader.lines().collect(Collectors.joining("\n"));
		} catch (Exception e) {
			throw new RuntimeException("Failed to load prompt template: " + resourcePath, e);
		}
	}
}
