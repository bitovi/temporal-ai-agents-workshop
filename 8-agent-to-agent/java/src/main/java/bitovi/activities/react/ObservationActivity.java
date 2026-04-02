package bitovi.activities.react;

import java.util.List;

import bitovi.activities.types.ObservationResponse;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.workflow.types.UsageMetadata;
import io.temporal.failure.ApplicationFailure;

public class ObservationActivity {
        public static ObservationResponse execute(String promptTemplate, String thought, String actionName,
                        String actionInputs, String actionResult)
                        throws ApplicationFailure {
                try {
                        System.out.println("observationActivity called with action result length: " +
                                        actionResult.length());

                        // If the output from the action is fairly small, we can just directly return it
                        // as the observation instead of doing a whole LLM call
                        if (actionResult.length() < 2048) {
                                System.out.println("Action result is small, returning directly as observation");
                                return new ObservationResponse(actionResult, new UsageMetadata(0, 0, 0));
                        }

                        EventClient.emitEvent("status", "Observing...", EventClient.LANE_CLIENT, null);
                        // Format prompt
                        String systemPrompt = promptTemplate
                                        .replace("{thought}", thought)
                                        .replace("{actionName}", actionName)
                                        .replace("{actionInputs}", actionInputs)
                                        .replace("{actionResult}", actionResult);

                        // Call Bedrock with low-quality model for cost optimization
                        Config config = new Config();
                        String modelId = config.getProperty("AWS_LOW_MODEL_ID");

                        AWS.ModelResponseWithUsage response = AWS.bedrockConverseWithUsage(
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
                        EventClient.emitEvent("observation", observations, EventClient.LANE_CLIENT, null);

                        return new ObservationResponse(observations, response.usage());

                } catch (Exception e) {
                        String errorMsg = "Error in observationActivity: " + e.getMessage();
                        System.err.println(errorMsg);
                        EventClient.emitEvent("error", "Observation error: " + errorMsg, EventClient.LANE_CLIENT, null);
                        throw ApplicationFailure.newFailure("observationActivity failed: " + e.getMessage(),
                                        "ObservationActivityError");
                }
        }
}
