package bitovi.activities.types;

import java.util.List;

import bitovi.workflow.types.UsageMetadata;

/**
 * Represents the response from a planning activity, including the list of plan steps.
 */
public record PlanResponse(
        List<PlanStep> steps, UsageMetadata usageMetadata) {
}
