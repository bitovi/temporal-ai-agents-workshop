package bitovi.activities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import bitovi.activities.react.ActionActivity;
import bitovi.activities.react.CompactActivity;
import bitovi.activities.react.ObservationActivity;
import bitovi.activities.react.ThoughtActivity;
import bitovi.activities.types.ActionInput;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.LabeledMemoryRecord;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.activities.types.ThoughtResponse;
import bitovi.common.Config;
import bitovi.common.aws.AgentCoreMemory;
import bitovi.common.local.LocalMemory;
import bitovi.common.local.RawEventHelper;
import bitovi.common.local.SemanticHelper;
import bitovi.common.local.UserPreferenceHelper;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.UsageMetadata;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class ActivitiesImpl implements Activities {

	@Override
	public ThoughtResponse thoughtActivity(List<ContextEntry> context, List<LabeledMemoryRecord> memoryRecords)
			throws ApplicationFailure {
		String promptTemplate = loadPromptTemplate("/prompts/thought-prompt.txt");
		return ThoughtActivity.execute(promptTemplate, context, memoryRecords);
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
	public CompactResponse compactActivity(List<ContextEntry> context) throws ApplicationFailure {
		String systemPromptTemplate = loadPromptTemplate("/prompts/compact-prompt.txt");
		return CompactActivity.execute(systemPromptTemplate, context);
	}

	@Override
	public void persistActivity(List<PersistMessage> messages) throws ApplicationFailure {
		try {
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

	@Override
	public void persistMemoryActivity(List<ContextEntry> entries, String sessionId) throws ApplicationFailure {
		Config config = new Config();

		// Pass the messages to the appropriate memory extraction system
		String useLocalExtraction = config.getProperty("LOCAL_MEMORY_EXTRACTION");
		if (useLocalExtraction.equals("true")) {
			// Save the messages to the local database in raw form.
			try {
				RawEventHelper.persistSessionEventsImpl(sessionId, entries);
			} catch (InterruptedException | ExecutionException e) {
				System.err.println("Error in RawEventHelper.persistSessionEventsImpl: " + e.getMessage());
				throw ApplicationFailure.newFailure("persistSessionEventsImpl failed: " + e.getMessage(),
						"PersistLocalMemoryActivityError");
			}

			// Send the messages to the local memory extraction system.
			try {
				LocalMemory.createEvent(entries, sessionId);
			} catch (Exception e) {
				System.err.println("Error in local LocalMemory.createEvent: " + e.getMessage());
				throw ApplicationFailure.newFailure("LocalMemory.createEvent failed: " + e.getMessage(),
						"PersistLocalMemoryActivityError");
			}
		} else {
			// Send the messages to the AgentCore memory extraction system.
			try {
				AgentCoreMemory.createEvent(entries);
			} catch (Exception e) {
				System.err.println("Error in AgentCoreMemory.createEvent: " + e.getMessage());
				throw ApplicationFailure.newFailure("AgentCoreMemory.createEvent failed: " + e.getMessage(),
						"PersistAgentCoreMemoryActivityError");
			}
		}
	}

	@Override
	public RetrieveMemoryRecordsResult retrieveMemoryRecordsActivity(String query,
			List<MemoryStrategyType> strategyTypes)
			throws ApplicationFailure {

		Config config = new Config();
		String useLocalExtraction = config.getProperty("LOCAL_MEMORY_EXTRACTION");
		if (useLocalExtraction.equals("true")) {
			try {
				return LocalMemory.retrieveMemoryRecords(query, strategyTypes);
			} catch (Exception e) {
				System.err.println("Error in local LocalMemory.retrieveMemoryRecords: " + e.getMessage());
				throw ApplicationFailure.newFailure("LocalMemory.retrieveMemoryRecords failed: " + e.getMessage(),
						"RetrieveLocalMemoryRecordsActivityError");
			}
		}
		try {
			RetrieveMemoryRecordsResponse response = AgentCoreMemory.retrieveMemoryRecords(query, strategyTypes);
			if (!response.hasMemoryRecordSummaries()) {
				return new RetrieveMemoryRecordsResult(List.of()); // empty
			}

			var memoryRecords = new ArrayList<LabeledMemoryRecord>();

			for (MemoryRecordSummary memoryRecordSummary : response.memoryRecordSummaries()) {
				MemoryStrategyType memoryStrategyType = AgentCoreMemory.getMemoryStrategyType(memoryRecordSummary);
				MemoryContent memoryContent = memoryRecordSummary.content();
				if (memoryContent.type() != MemoryContent.Type.TEXT) {
					continue;
				}

				String text = memoryContent.text();
				memoryRecords.add(new LabeledMemoryRecord(memoryStrategyType, text));
			}
			return new RetrieveMemoryRecordsResult(memoryRecords);
		} catch (Exception e) {
			System.err.println("Error in AgentCoreMemory.retrieveMemoryRecords: " + e.getMessage());
			throw ApplicationFailure.newFailure("AgentCoreMemory.retrieveMemoryRecords failed: " + e.getMessage(),
					"RetrieveAgentCoreMemoryRecordsActivityError");
		}
	}

	@Override
	public Integer getTokenUsage(List<ContextEntry> context) throws ApplicationFailure {
		int totalChars = context.stream().mapToInt(entry -> entry.content() != null ? entry.content().length() : 0)
				.sum();
		int estimatedTokens = totalChars / 4;
		return estimatedTokens;
	}

	@Override
	public UsageMetadata extractUserPreferenceMemories(String userId, String sessionId, List<ContextEntry> entries)
			throws ApplicationFailure {
		try {
			String extractTemplate = loadPromptTemplate("/prompts/extract-user-pref.txt");
			String consolidateTemplate = loadPromptTemplate("/prompts/consolodate-user-pref.txt");
			UserPreferenceHelper userPreferenceHelper = new UserPreferenceHelper();
			UsageMetadata usageMetadata = userPreferenceHelper.extractUserPreferenceMemoriesImpl(extractTemplate,
					consolidateTemplate,
					sessionId, entries);
			userPreferenceHelper.close();
			return usageMetadata;
		} catch (InterruptedException | ExecutionException e) {
			throw ApplicationFailure.newFailure("extractUserPreferenceMemories failed: " + e.getMessage(),
					"ExtractUserPreferenceMemoriesError");
		}
	}

	@Override
	public UsageMetadata extractSemanticMemories(String userId, String sessionId, List<ContextEntry> entries)
			throws ApplicationFailure {
		try {
			String promptTemplate = loadPromptTemplate("/prompts/extract-semantic.txt");
			String consolidateTemplate = loadPromptTemplate("/prompts/consolodate-semantic.txt");
			SemanticHelper semanticHelper = new SemanticHelper();
			UsageMetadata usageMetadata = semanticHelper.extractSemanticMemoriesImpl(promptTemplate,
					consolidateTemplate, sessionId,
					entries);
			semanticHelper.close();
			return usageMetadata;
		} catch (InterruptedException | ExecutionException e) {
			throw ApplicationFailure.newFailure("extractSemanticMemories failed: " + e.getMessage(),
					"ExtractSemanticMemoriesError");
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
}