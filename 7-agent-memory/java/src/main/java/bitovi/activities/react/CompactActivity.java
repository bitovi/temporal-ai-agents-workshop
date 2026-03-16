package bitovi.activities.react;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import bitovi.activities.types.CompactResponse;
import bitovi.common.Config;
import bitovi.common.EventClient;
import bitovi.common.ModelUtils;
import bitovi.common.aws.BedrockConverse;
import bitovi.common.aws.BedrockConverse.ChatMessage;
import bitovi.common.aws.BedrockConverse.ModelResponseWithUsage;
import bitovi.workflow.types.ContextEntry;
import bitovi.workflow.types.ContextEntryType;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

public class CompactActivity {
   	public static CompactResponse execute(String promptTemplate, List<ContextEntry> context) throws ApplicationFailure {
try {
			System.out.println("compactActivity called with context size: " + context.size());
			EventClient.emitEvent("status", "Compacting...");

			// Convert ContextEntry list to XML strings for LLM prompt
			List<String> contextStrings = context.stream()
					.map(ContextEntry::toXMLString)
					.collect(Collectors.toList());

			// Truncate context
			List<String> truncatedContext = ModelUtils.truncateContextToTokenLimit(contextStrings);

			// Format prompt
			String systemPrompt = promptTemplate
					.replace("{contextHistory}", String.join("\n", truncatedContext));

			// Call Bedrock with low-quality model for cost optimization
			Config config = new Config();
			String modelId = config.getProperty("AWS_LOW_MODEL_ID");

			ModelResponseWithUsage response = BedrockConverse.bedrockConverseWithUsage(
					systemPrompt,
					List.of(new ChatMessage("user", systemPrompt)),
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
					null);
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
 
}
