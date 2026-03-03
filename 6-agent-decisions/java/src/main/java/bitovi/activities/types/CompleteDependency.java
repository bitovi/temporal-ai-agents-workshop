package bitovi.activities.types;

/**
 * Represents a completed dependency for a task, including its ID, result type,
 * and result value.
 */
public record CompleteDependency(String id, String result_type, String result) {
}