package bitovi.activities;

import java.util.List;

import bitovi.activities.types.ObservationResponse;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import io.temporal.failure.ApplicationFailure;

public class Observation {
    public static ObservationResponse execute(String promptTemplate, List<String> context, String actionResult) {
        try {
            System.out.println("observationActivity called with action result length: " +
                    actionResult.length());
                    
            // Truncate context
            List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(context);

            // Format prompt
            String instructions = promptTemplate
                    .replace("{previousSteps}", String.join("\n", truncatedContext))
                    .replace("{actionResult}", actionResult);

            // Call Bedrock with low-quality model for cost optimization
            Config config = new Config();
            String modelId = config.getProperty("AWS_LOW_MODEL_ID");

            AWS.ModelResponseWithUsage response = AWS.bedrockConverseWithUsage(
                    "You are a helpful and friendly AI agent, named Hennos, making observations based on action results and context.",
                    // must start with a user message
                    List.of(new ChatMessage("user", instructions)),
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
}
