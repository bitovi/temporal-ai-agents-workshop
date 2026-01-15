package bitovi.activities.DTO;

public record PersistMessage(
	String role,
	String message,
	String date,
	String name
) {
}
