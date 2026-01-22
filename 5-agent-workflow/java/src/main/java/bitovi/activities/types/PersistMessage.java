package bitovi.activities.types;

public record PersistMessage(
	String role,
	String message,
	String date,
	String name
) {
}
