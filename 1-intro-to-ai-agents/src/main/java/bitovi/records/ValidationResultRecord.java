package bitovi.records;

/**
 * ValidationResultRecord is a record class that encapsulates the result of a
 * validation operation.
 */
public record ValidationResultRecord(boolean isValid, String message) {
    // Record classes, which are a special kind of class, help to model plain data
    // aggregates with less ceremony than normal classes.
}