package bitovi.activities.types;

import java.util.ArrayList;

/**
 * Represents the response from a planning activity, including the list of plan steps.
 */
public record PlanResponse(
        ArrayList<PlanStep> steps) {
}
