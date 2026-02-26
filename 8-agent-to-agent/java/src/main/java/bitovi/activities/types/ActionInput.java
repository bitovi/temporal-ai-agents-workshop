package bitovi.activities.types;

import java.util.Map;

/**
 * Input parameter wrapper for tool action execution.
 * Ensures clean JSON serialization by explicitly typing the parameters map.
 */
public record ActionInput(
    Map<String, Object> parameters
) {
}
