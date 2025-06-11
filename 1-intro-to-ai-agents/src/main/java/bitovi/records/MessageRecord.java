package bitovi.records;

/**
 * Represents a message record with a role and content.
 * This is used to store chat messages in a structured format.
 */
public record MessageRecord(String role, String content) {
    // Record classes, which are a special kind of class, help to model plain data
    // aggregates with less ceremony than normal classes.
}
