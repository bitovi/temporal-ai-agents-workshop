package bitovi.activities.react;

import java.util.List;
import java.util.stream.Collectors;

import bitovi.activities.types.ObservationResponse;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import bitovi.common.aws.BedrockConverse;
import bitovi.common.aws.BedrockConverse.ChatMessage;
import bitovi.common.aws.BedrockConverse.ModelResponseWithUsage;
import bitovi.workflow.types.ContextEntry;
import io.temporal.failure.ApplicationFailure;

public class ObservationActivity {
    public static ObservationResponse execute(String promptTemplate, List<ContextEntry> context, String actionResult)
            throws ApplicationFailure {
        try {
            System.out.println("observationActivity called with action result length: " +
                    actionResult.length());
            EventClient.emitEvent("status", "Observing...");

            // Convert ContextEntry list to XML strings for LLM prompt
            List<String> contextStrings = context.stream()
                    .map(ContextEntry::toXmlString)
                    .collect(Collectors.toList());

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
                    List.of(new ChatMessage("user", systemPrompt)),
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
