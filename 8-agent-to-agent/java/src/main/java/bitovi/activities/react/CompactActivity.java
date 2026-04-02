package bitovi.activities.react;

import java.util.ArrayList;
import java.util.List;

import bitovi.activities.types.CompactResponse;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import io.temporal.failure.ApplicationFailure;

public class CompactActivity {
    public static CompactResponse compactActivity(String promptTemplate, List<String> context)
            throws ApplicationFailure {
        try {
            System.out.println("compactActivity called with context size: " + context.size());
            EventClient.emitEvent("status", "Compacting...", EventClient.LANE_CLIENT, null);

            // Load prompt template

            // Truncate context
            List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(context);

            // Format prompt
            String systemPrompt = promptTemplate
                    .replace("{contextHistory}", String.join("\n", truncatedContext));

            // Call Bedrock with low-quality model for cost optimization
            Config config = new Config();
            String modelId = config.getProperty("AWS_LOW_MODEL_ID");

            AWS.ModelResponseWithUsage response = AWS.bedrockConverseWithUsage(
                    systemPrompt,
                    List.of(new ChatMessage("user", "perform COMPACTION")),
                    null,
                    modelId);

            String compactedSummary = response.response();
            if (compactedSummary == null || compactedSummary.isEmpty()) {
                compactedSummary = "Context summary";
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
            EventClient.emitEvent("compact", "Context compacted", EventClient.LANE_CLIENT, null);

            return new CompactResponse(newContext, response.usage());

        } catch (Exception e) {
            String errorMsg = "Error in compactActivity: " + e.getMessage();
            System.err.println(errorMsg);
            EventClient.emitEvent("error", "Compact error: " + errorMsg, EventClient.LANE_CLIENT, null);
            throw ApplicationFailure.newFailure("compactActivity failed: " + e.getMessage(),
                    "CompactActivityError");
        }
    }
}
