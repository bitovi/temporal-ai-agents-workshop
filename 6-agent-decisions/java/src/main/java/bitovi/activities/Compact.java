package bitovi.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.activities.types.CompactResponse;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import io.temporal.failure.ApplicationFailure;

public class Compact {
    public static CompactResponse execute(String promptTemplate, List<String> context) {
        try {
            System.out.println("compactActivity called with context size: " + context.size());

            // Truncate context
            List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(context);

            // Format prompt
            String instructions = promptTemplate
                    .replace("{contextHistory}", String.join("\n", truncatedContext));

            // Call Bedrock with low-quality model for cost optimization
            Config config = new Config();
            String modelId = config.getProperty("AWS_LOW_MODEL_ID");

            AWS.ModelResponseWithUsage response = AWS.bedrockConverseWithUsage(
                    "You are a expert at summarizing information, keeping important details intact.",
                    List.of(new ChatMessage("user", instructions)),
                    null,
                    modelId);

            String compactedSummary = response.response();
            if (compactedSummary == null || compactedSummary.isEmpty()) {
                throw ApplicationFailure.newFailure("Empty response from model", "EmptyModelResponse");
            }

            // Build result: [compactedSummary, ...last 3 entries]
            List<String> newContext = new ArrayList<>();
            newContext.add(compactedSummary);

            // Add last N entries from original context
            int entriesToKeep = Math.min(3, context.size());
            if (entriesToKeep > 0) {
                List<String> recentEntries = context.subList(
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
}
