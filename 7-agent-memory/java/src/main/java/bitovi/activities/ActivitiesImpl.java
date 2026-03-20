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
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.activities.types.ThoughtResponse;
import bitovi.common.Config;
import bitovi.common.aws.AgentCoreMemory;
import bitovi.common.local.LocalMemory;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.UsageMetadata;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class ActivitiesImpl implements Activities {

	@Override
	public ThoughtResponse thoughtActivity(List<ContextEntry> context, List<String> memoryRecords)
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

	@Override
	public void persistMemoryActivity(List<ContextEntry> entries) throws ApplicationFailure {
		Config config = new Config();
		String useLocalExtraction = config.getProperty("LOCAL_MEMORY_EXTRACTION");
		if (useLocalExtraction.equals("true")) {
			try {
				LocalMemory.createEvent(entries);
			} catch (Exception e) {
				System.err.println("Error in local persistMemoryActivity: " + e.getMessage());
				throw ApplicationFailure.newFailure("persistMemoryActivity failed: " + e.getMessage(),
						"PersistLocalMemoryActivityError");
			}
		} else {
			try {
				AgentCoreMemory.createEvent(entries);
			} catch (Exception e) {
				System.err.println("Error in AgentCore persistMemoryActivity: " + e.getMessage());
				throw ApplicationFailure.newFailure("persistMemoryActivity failed: " + e.getMessage(),
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
				System.err.println("Error in local retrieveMemoryRecordsActivity: " + e.getMessage());
				throw ApplicationFailure.newFailure("retrieveMemoryRecordsActivity failed: " + e.getMessage(),
						"RetrieveLocalMemoryRecordsActivityError");
			}
		}
		try {

			if (query.length() > 1000) {
				System.out.println("Search query exceeds 1000 characters, truncating to 1000 characters");
				query = query.substring(0, 999);
			}

			RetrieveMemoryRecordsResponse response = AgentCoreMemory.retrieveMemoryRecords(query, strategyTypes);
			if (response.memoryRecordSummaries().isEmpty()) {
				return new RetrieveMemoryRecordsResult(List.of()); // empty
			}

			var memoryRecords = new ArrayList<String>();
			for (MemoryRecordSummary memoryRecordSummary : response.memoryRecordSummaries()) {
				MemoryStrategyType memoryStrategyType = AgentCoreMemory.getMemoryStrategyType(memoryRecordSummary);
				MemoryContent memoryContent = memoryRecordSummary.content();
				if (memoryContent.type() != MemoryContent.Type.TEXT) {
					continue;
				}
				String text = memoryContent.text();

				String typeTag = memoryStrategyType.toString().toLowerCase().replace("_", "-");
				memoryRecords.add(String.format("<%s>%s</%s>", typeTag, text, typeTag));
			}
			return new RetrieveMemoryRecordsResult(memoryRecords);
		} catch (Exception e) {
			System.err.println("Error in AgentCore retrieveMemoryRecordsActivity: " + e.getMessage());
			throw ApplicationFailure.newFailure("retrieveMemoryRecordsActivity failed: " + e.getMessage(),
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
	public UsageMetadata extractUserPreferenceMemories(String userId, List<ContextEntry> entries)
			throws ApplicationFailure {
		System.out.println("extractUserPreferenceMemories called with userId: " + userId + " and entries: " + entries);
		return LocalMemory.extractUserPreferenceMemoriesImpl(entries);
	}

	@Override
	public UsageMetadata extractSemanticMemories(String userId, List<ContextEntry> entries) throws ApplicationFailure {
		System.out.println("extractSemanticMemories called with userId: " + userId + " and entries: " + entries);
		return LocalMemory.extractSemanticMemoriesImpl(entries);
	}

	@Override
	public void initializeMemoryStorage(String userId) throws ApplicationFailure {
		System.out.println("initializeMemoryStorage called with userId: " + userId);
		try {
			LocalMemory.initializeMemoryStorage();
		} catch (InterruptedException | ExecutionException e) {
			throw ApplicationFailure.newFailure("initializeMemoryStorage failed: " + e.getMessage(),
					"InitializeMemoryStorageError");
		}
	}
}