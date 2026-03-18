package bitovi.activities.react;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import bitovi.activities.tools.ToolRegistry;
import bitovi.activities.types.ActionDetail;
import bitovi.activities.types.ActionInput;
import bitovi.activities.types.ThoughtResponse;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import bitovi.common.aws.BedrockConverse;
import bitovi.common.aws.BedrockConverse.ChatMessage;
import bitovi.common.aws.BedrockConverse.ModelResponseWithUsage;
import bitovi.workflow.types.ContextEntry;
import io.temporal.failure.ApplicationFailure;

public class ThoughtActivity {
	public static ThoughtResponse execute(String promptTemplate, List<ContextEntry> context, List<String> memoryRecords)
			throws ApplicationFailure {
		try {
			EventClient.emitEvent("status", "Thinking...");

			// Convert ContextEntry list to XML strings for LLM prompt
			List<String> contextStrings = context.stream()
					.map(ContextEntry::toXMLString)
					.collect(Collectors.toList());

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
					.replace("{memoryRecords}", String.join("\n", memoryRecords))
					.replace("{availableActions}", availableActions);

			// Call Bedrock with high-quality model
			Config config = new Config();
			String modelId = config.getProperty("AWS_MODEL_ID");

			ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
					systemPrompt,
					// Must start with a user message
					List.of(new ChatMessage("user", systemPrompt)),
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
					System.out.println("Warning: Unexpected input type "
							+ (inputObj == null ? "null" : inputObj.getClass().getName()) +
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
		} catch (ApplicationFailure e) {
			String errorMsg = "Error in thoughtActivity: " + e.getMessage();
			System.err.println(errorMsg);
			EventClient.emitEvent("error", "Thought error: " + errorMsg);
			throw ApplicationFailure.newFailure("thoughtActivity failed: " + e.getMessage(),
					"ThoughtActivityError");
		}
	}
}
