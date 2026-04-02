package bitovi.activities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

import bitovi.activities.react.ActionActivity;
import bitovi.activities.react.CompactActivity;
import bitovi.activities.react.ObservationActivity;
import bitovi.activities.react.ThoughtActivity;
import bitovi.activities.types.ActionInput;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.ThoughtResponse;
import bitovi.common.ModelUtils;
import io.temporal.failure.ApplicationFailure;

public class ActivitiesImpl implements Activities {

	@Override
	public ThoughtResponse thoughtActivity(List<String> context) throws ApplicationFailure {
		// Load prompt template
		String promptTemplate = loadPromptTemplate("/prompts/thought-prompt.txt");
		return ThoughtActivity.thoughtActivity(promptTemplate, context);
	}

	@Override
	public String actionActivity(String toolName, ActionInput input) throws ApplicationFailure {
		return ActionActivity.execute(toolName, input);
	}

	@Override
	public ObservationResponse observationActivity(String thought, String actionName, String actionInputs,
			String actionResult)
			throws ApplicationFailure {
		String promptTemplate = loadPromptTemplate("/prompts/observation-prompt.txt");
		return ObservationActivity.execute(promptTemplate, thought, actionName, actionInputs, actionResult);
	}

	@Override
	public CompactResponse compactActivity(List<String> context) throws ApplicationFailure {
		String systemPromptTemplate = loadPromptTemplate("/prompts/compact-prompt.txt");
		return CompactActivity.compactActivity(systemPromptTemplate, context);
	}

	@Override
	public void persistActivity(List<PersistMessage> messages) throws ApplicationFailure {
		try {
			System.out.println("persistActivity called with " + messages.size() + " messages:");

			for (PersistMessage msg : messages) {
				if ("user".equals(msg.role())) {
					System.out.println(String.format("  %s (%s): %s",
							msg.name(), msg.date(), msg.message()));
				} else if ("assistant".equals(msg.role())) {
					System.out.println(String.format("  assistant: %s", msg.message()));
				}
			}

		} catch (Exception e) {
			System.err.println("Error in persistActivity: " + e.getMessage());
			throw ApplicationFailure.newFailure("persistActivity failed: " + e.getMessage(),
					"PersistActivityError");
		}
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

	@Override
	public Integer getTokenUsage(List<String> context) throws ApplicationFailure {
		int total = 0;
		for (String entry : context) {
			total += ModelUtils.estimateTokenCount(entry);
		}
		return total;
	}
}
