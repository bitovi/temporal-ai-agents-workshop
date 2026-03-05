package bitovi.activities.types;

import java.util.List;

/**
 * Represents the response from a planning activity, including the list of plan steps.
 */
public record PlanResponse(
        List<PlanStep> steps) {
}
