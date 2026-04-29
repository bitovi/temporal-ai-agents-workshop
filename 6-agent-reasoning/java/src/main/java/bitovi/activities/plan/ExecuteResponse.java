package bitovi.activities.plan;

import java.util.List;

import bitovi.activities.types.FinalResponse;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import io.temporal.failure.ApplicationFailure;

public class ExecuteResponse {
  public static FinalResponse execute(String promptTemplate, List<String> context) throws ApplicationFailure {
    System.out.println("planActivity called with context size: " + context.size());
    EventClient.emitEvent("status", "Responding...");

    // Truncate context
    List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(context);

    // Format prompt with placeholders
    String systemPrompt = promptTemplate
        .replace("{previousSteps}", String.join("\n", truncatedContext));

    // Call Bedrock with high-quality model
    Config config = new Config();
    String modelId = config.getProperty("AWS_MODEL_ID");

    AWS.ModelResponseWithUsage response = AWS.bedrockConverseWithUsage(
        "You are a helpful and friendly AI agent.",
        // Must start with a user message
        List.of(new ChatMessage("user", systemPrompt)),
        null, // No tool config needed for response
        modelId);

    String responseText = response.response();
    if (responseText == null || responseText.isEmpty()) {
      throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
    }

    System.out.println("Model response: " + responseText);
    return new FinalResponse(responseText, response.usage());
  }
}
