package bitovi.activities.types;

import bitovi.workflow.types.UsageMetadata;

public record FinalResponse(
        String response, UsageMetadata usageMetadata) {
}