package bitovi.activities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import bitovi.activities.tools.ToolRegistry;
import bitovi.activities.types.ActionDetail;
import bitovi.activities.types.ActionInput;
import bitovi.activities.types.CompactResponse;
import bitovi.activities.types.ObservationResponse;
import bitovi.activities.types.PersistMessage;
import bitovi.activities.types.RetrieveMemoryRecordsResult;
import bitovi.activities.types.ThoughtResponse;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import bitovi.common.aws.AgentCoreMemory;
import bitovi.common.aws.BedrockConverse;
import bitovi.common.aws.BedrockConverse.ChatMessage;
import bitovi.common.aws.BedrockConverse.ModelResponseWithUsage;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.ContextEntryType;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class ActivitiesImpl implements Activities {

    private static Config config = new Config();

	@Override
	public ThoughtResponse thoughtActivity(List<ContextEntry> context, List<String> memoryRecords) throws ApplicationFailure {
		try {
			System.out.println("thoughtActivity called with context size: " + context.size());

			// Convert ContextEntry list to XML strings for LLM prompt
			List<String> contextStrings = context.stream()
					.map(ContextEntry::toXmlString)
					.collect(Collectors.toList());

			// Load prompt template
			String promptTemplate = loadPromptTemplate("/prompts/thought-prompt.txt");

			// Get current date
			String currentDate = LocalDate.now().toString();

			// Truncate context
			List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(contextStrings);

			// Get available tools as XML string
			String availableActions = ToolRegistry.getToolsAsXmlString();

			// Format prompt with placeholders
			String systemPrompt = promptTemplate
					.replace("{currentDate}", currentDate)
					.replace("{previousSteps}", String.join("\n", truncatedContext))
					.replace("{userPreferences}", String.join("\n", memoryRecords))
					.replace("{availableActions}", availableActions);

			System.out.println("[THOUGHT] systemPrompt: " + systemPrompt);

			// Call Bedrock with high-quality model
			Config config = new Config();
			String modelId = config.getProperty("AWS_MODEL_ID");

			ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
					systemPrompt,
					// Must start with a user message
					List.of(new ChatMessage("user", "perform THOUGHT")),
					null, // No tool config needed for thought
					modelId);

			String responseText = response.response();
			if (responseText == null || responseText.isEmpty()) {
				throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
			}

			System.out.println("Model response: " + responseText);

			// Parse JSON response
			JSONObject jsonResponse = new JSONObject(responseText);
			String thought = jsonResponse.optString("thought", "");

			// Determine type based on fields present
			String type;
			String answer = null;
			ActionDetail action = null;

			if (jsonResponse.has("answer")) {
				type = "answer";
				answer = jsonResponse.getString("answer");
				// Emit events for answer type
				EventClient.emitEvent("thought", thought);
				EventClient.emitEvent("answer", answer);
			} else if (jsonResponse.has("action")) {
				type = "action";
				JSONObject actionObj = jsonResponse.getJSONObject("action");
				String name = actionObj.getString("name");
				String reason = actionObj.optString("reason", "");

				// Parse input as Map and wrap in ActionInput
				Object inputObj = actionObj.get("input");
				Map<String, Object> inputMap;
				if (inputObj instanceof JSONObject) {
					inputMap = ((JSONObject) inputObj).toMap();
				} else if (inputObj instanceof Map) {
					inputMap = (Map<String, Object>) inputObj;
				} else {
					// Fallback for unexpected input types
					System.out.println("Warning: Unexpected input type " + inputObj.getClass().getName() +
							", wrapping in 'value' key");
					inputMap = new HashMap<>();
					inputMap.put("value", inputObj);
				}

				// Validate non-null before creating ActionInput
				if (inputMap == null) {
					inputMap = new HashMap<>();
				}

				ActionInput actionInput = new ActionInput(inputMap);
				action = new ActionDetail(name, reason, actionInput);

				// Emit events for action type
				EventClient.emitEvent("thought", thought);
			} else {
				throw ApplicationFailure.newFailure("Invalid response format: missing 'answer' or 'action'",
						"InvalidResponseFormat");
			}

			return new ThoughtResponse(type, thought, answer, action, response.usage());

		} catch (JSONException e) {
			String errorMsg = "Error parsing JSON response: " + e.getMessage();
			System.err.println(errorMsg);
			EventClient.emitEvent("error", "Thought error: " + errorMsg);
			throw ApplicationFailure.newFailure("Failed to parse model response: " + e.getMessage(),
					"ThoughtActivityError");
		} catch (Exception e) {
			String errorMsg = "Error in thoughtActivity: " + e.getMessage();
			System.err.println(errorMsg);
			EventClient.emitEvent("error", "Thought error: " + errorMsg);
			throw ApplicationFailure.newFailure("thoughtActivity failed: " + e.getMessage(),
					"ThoughtActivityError");
		}
	}

	@Override
	public String actionActivity(String toolName, ActionInput input) throws ApplicationFailure {
		try {
			System.out.println("actionActivity called with tool: " + toolName);

			// Check if tool exists
			if (!ToolRegistry.hasToolNamed(toolName)) {
				EventClient.emitEvent("error", "Tool with name " + toolName + " not found.");
				JSONObject errorResult = new JSONObject();
				errorResult.put("name", toolName);
				errorResult.put("input", input.parameters());
				errorResult.put("error", "Tool not found");
				return errorResult.toString();
			}

			// Get parameters from ActionInput
			Map<String, Object> inputMap = input.parameters();

			// Execute tool
			try {
				EventClient.emitEvent("action", "Invoked tool " + toolName +
						" with input " + new JSONObject(inputMap).toString());

				String result = ToolRegistry.executeTool(toolName, inputMap);
				System.out.println("Tool execution successful: " + toolName);
				return result;
			} catch (Exception e) {
				String errorMsg = "Error executing tool " + toolName + ": " + e.getMessage();
				System.err.println(errorMsg);
				EventClient.emitEvent("error", errorMsg);
				JSONObject errorResult = new JSONObject();
				errorResult.put("name", toolName);
				errorResult.put("input", inputMap);
				errorResult.put("error", e.getMessage());
				return errorResult.toString();
			}

		} catch (Exception e) {
			System.err.println("Error in actionActivity: " + e.getMessage());
			throw ApplicationFailure.newFailure("actionActivity failed: " + e.getMessage(),
					"ActionActivityError");
		}
	}

	@Override
	public ObservationResponse observationActivity(List<ContextEntry> context, String actionResult)
			throws ApplicationFailure {
		try {
			System.out.println("observationActivity called with action result length: " +
					actionResult.length());

			// Convert ContextEntry list to XML strings for LLM prompt
			List<String> contextStrings = context.stream()
					.map(ContextEntry::toXmlString)
					.collect(Collectors.toList());

			// Load prompt template
			String promptTemplate = loadPromptTemplate("/prompts/observation-prompt.txt");

			// Truncate context
			List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(contextStrings);

			// Format prompt
			String systemPrompt = promptTemplate
					.replace("{previousSteps}", String.join("\n", truncatedContext))
					.replace("{actionResult}", actionResult);

			// Call Bedrock with low-quality model for cost optimization
			Config config = new Config();
			String modelId = config.getProperty("AWS_LOW_MODEL_ID");

			ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
					systemPrompt,
					// must start with a user message
					List.of(new ChatMessage("user", "perform OBSERVATION")),
					null,
					modelId);

			String observations = response.response();
			if (observations == null || observations.isEmpty()) {
				observations = "No observation generated";
			}

			// Emit observation event
			EventClient.emitEvent("observation", observations);

			return new ObservationResponse(observations, response.usage());

		} catch (Exception e) {
			String errorMsg = "Error in observationActivity: " + e.getMessage();
			System.err.println(errorMsg);
			EventClient.emitEvent("error", "Observation error: " + errorMsg);
			throw ApplicationFailure.newFailure("observationActivity failed: " + e.getMessage(),
					"ObservationActivityError");
		}
	}

	@Override
	public CompactResponse compactActivity(List<ContextEntry> context) throws ApplicationFailure {
		try {
			System.out.println("compactActivity called with context size: " + context.size());

			// Convert ContextEntry list to XML strings for LLM prompt
			List<String> contextStrings = context.stream()
					.map(ContextEntry::toXmlString)
					.collect(Collectors.toList());

			// Load prompt template
			String systemPromptTemplate = loadPromptTemplate("/prompts/compact-prompt.txt");

			// Truncate context
			List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(contextStrings);

			// Format prompt
			String systemPrompt = systemPromptTemplate
					.replace("{contextHistory}", String.join("\n", truncatedContext));

			// Call Bedrock with low-quality model for cost optimization
			Config config = new Config();
			String modelId = config.getProperty("AWS_LOW_MODEL_ID");

			ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
					systemPrompt,
					List.of(new ChatMessage("user", "perform COMPACTION")),
					null,
					modelId);

			String compactedSummary = response.response();
			if (compactedSummary == null || compactedSummary.isEmpty()) {
				compactedSummary = "Context summary";
			}

			// Build result: Create SUMMARY entry + last 3 entries from original context
			List<ContextEntry> newContext = new ArrayList<>();
			
			// Create SUMMARY entry with compacted content
			ContextEntry summaryEntry = new ContextEntry(
				Instant.now(),
				Role.ASSISTANT,
				compactedSummary,
				ContextEntryType.SUMMARY,
				null,
				null,
				null
			);
			newContext.add(summaryEntry);

			// Add last N entries from original context
			int entriesToKeep = Math.min(3, context.size());
			if (entriesToKeep > 0) {
				List<ContextEntry> recentEntries = context.subList(
						context.size() - entriesToKeep,
						context.size());
				newContext.addAll(recentEntries);
			}

			System.out.println("Context compacted from " + context.size() + " to " +
					newContext.size() + " entries");

			// Emit compact event
			EventClient.emitEvent("compact", "Context compacted");

			return new CompactResponse(newContext, response.usage());

		} catch (Exception e) {
			String errorMsg = "Error in compactActivity: " + e.getMessage();
			System.err.println(errorMsg);
			EventClient.emitEvent("error", "Compact error: " + errorMsg);
			throw ApplicationFailure.newFailure("compactActivity failed: " + e.getMessage(),
					"CompactActivityError");
		}
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
	public void persistMemoryActivity(List<ContextEntry> entries) throws ApplicationFailure {

		try {
			AgentCoreMemory.createEvent(entries);
		}
		catch (Exception e) {
			System.err.println("Error in persistMemoryActivity: " + e.getMessage());
			throw ApplicationFailure.newFailure("persistMemoryActivity failed: " + e.getMessage(),
					"PersistMemoryActivityError");
		}
	}

	@Override
	public RetrieveMemoryRecordsResult retrieveMemoryRecordsActivity(String query, MemoryStrategyType strategyType) throws ApplicationFailure {
		try {
			RetrieveMemoryRecordsResponse response = AgentCoreMemory.retrieveMemoryRecords(query, strategyType);
			if (response.memoryRecordSummaries().isEmpty()) {
				return new RetrieveMemoryRecordsResult(List.of()); // empty
			}

			var memoryRecords = new ArrayList<String>();
			for (MemoryRecordSummary memoryRecordSummary : response.memoryRecordSummaries()) {
				MemoryStrategyType memoryStrategyType = AgentCoreMemory.getMemoryStrategyType(memoryRecordSummary);
				MemoryContent memoryContent = memoryRecordSummary.content();
				if (memoryContent.type() != MemoryContent.Type.TEXT) { continue; }
				String text = memoryContent.text();

				String typeTag = memoryStrategyType.toString().toLowerCase().replace("_", "-");
				memoryRecords.add(String.format("<%s>%s</%s>", typeTag, text, typeTag));
			}
			return new RetrieveMemoryRecordsResult(memoryRecords);
		}
		catch (Exception e) {
			System.err.println("Error in retrieveMemoryRecordsActivity: " + e.getMessage());
			throw ApplicationFailure.newFailure("retrieveMemoryRecordsActivity failed: " + e.getMessage(),
					"RetrieveMemoryRecordsActivityError");
		}
	}
}
