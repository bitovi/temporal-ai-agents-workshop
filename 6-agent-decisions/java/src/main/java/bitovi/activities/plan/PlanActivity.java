package bitovi.activities.plan;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import bitovi.activities.tools.ToolRegistry;
import bitovi.activities.types.ActionInput;
import bitovi.activities.types.PlanResponse;
import bitovi.activities.types.PlanStep;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import io.temporal.failure.ApplicationFailure;

public class PlanActivity {
  public static PlanResponse execute(String promptTemplate, List<String> context) throws ApplicationFailure {
    System.out.println("planActivity called with context size: " + context.size());
    EventClient.emitEvent("status", "Planning...");

    // Truncate context
    List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(context);

    // Get available tools as XML string
    String availableActions = ToolRegistry.getToolsAsXmlString();

    // Format prompt with placeholders
    String systemPrompt = promptTemplate
        .replace("{previousSteps}", String.join("\n", truncatedContext))
        .replace("{availableActions}", availableActions);

    // Call Bedrock with high-quality model
    Config config = new Config();
    String modelId = config.getProperty("AWS_MODEL_ID");

    AWS.ModelResponseWithUsage response = AWS.bedrockConverseWithUsage(
        "You are a helpful and friendly AI agent, named Hennos, making decisions based on context and available actions.",
        // Must start with a user message
        List.of(new ChatMessage("user", systemPrompt)),
        null, // No tool config needed for thought
        modelId);

    String responseText = response.response();
    if (responseText == null || responseText.isEmpty()) {
      throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
    }

    System.out.println("Model response: " + responseText);

    JSONObject jsonResponse = new JSONObject(responseText);

    // Check that the format of the steps is correct
    if (!jsonResponse.has("steps")) {
      throw ApplicationFailure.newFailure("Model response missing 'steps' field", "InvalidModelResponse");
    }

    // Make sure that steps is an array
    if (!jsonResponse.get("steps").getClass().equals(org.json.JSONArray.class)) {
      throw ApplicationFailure.newFailure("'steps' field is not an array", "InvalidModelResponse");
    }

    JSONArray planSteps = jsonResponse.getJSONArray("steps");

    // For each step, check that it has the required fields
    List<PlanStep> steps = new ArrayList<>();
    for (int i = 0; i < planSteps.length(); i++) {
      JSONObject step = planSteps.getJSONObject(i);
      if (!step.has("id") || !step.has("tool_name") || !step.has("tool_input") || !step.has("dependsOn")) {
        throw ApplicationFailure.newFailure("Step missing required fields", "InvalidModelResponse");
      }

      // Make sure that dependsOn is an array
      if (!step.get("dependsOn").getClass().equals(org.json.JSONArray.class)) {
        throw ApplicationFailure.newFailure("'dependsOn' field is not an array", "InvalidModelResponse");
      }

      // If we made it here, the step is valid and we can add it to the list of steps
      steps.add(new PlanStep(
          step.getInt("id"),
          step.getString("tool_name"),
          new ActionInput(step.getJSONObject("tool_input").toMap()),
          null, // Placeholder for result type, can be extended in the future
          step.getJSONArray("dependsOn").toList().stream().map(Object::toString).map(Integer::parseInt).toList()));
    }

    // If we made it here, the response is valid and we can return it
    return new PlanResponse(steps);
  }
}
