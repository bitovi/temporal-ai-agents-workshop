package bitovi.records;

/**
 * Represents a message record with a role and content.
 * This is used to store chat messages in a structured format.
 */
public record MessageRecord(String role, String content) {
    // https://docs.oracle.com/en/java/javase/17/language/records.html
}
