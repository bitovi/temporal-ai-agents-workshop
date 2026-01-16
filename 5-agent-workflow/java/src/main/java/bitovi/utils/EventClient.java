package bitovi.utils;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.json.JSONObject;

/**
 * EventClient sends events to the agent-chat-server via HTTP POST requests.
 * This enables real-time updates to be sent to the web UI through Server-Sent Events (SSE).
 * 
 * Events are sent asynchronously in a fire-and-forget manner - failures are silently ignored
 * to prevent activity errors from blocking workflow execution.
 */
public class EventClient {
	private static final String SERVER_URL = System.getenv().getOrDefault("SERVER_URL", "http://localhost:3000");
	private static final String EMIT_EVENT_ENDPOINT = SERVER_URL + "/api/emit-event";
	private static final int TIMEOUT_MS = 5000;

	/**
	 * Emit an event to the server asynchronously.
	 * 
	 * @param type The event type (e.g., "thought", "answer", "action", "observation", "error")
	 * @param message The event message content
	 * @param additionalData Optional additional data to include in the event
	 */
	public static void emitEvent(String type, String message, Map<String, Object> additionalData) {
		CompletableFuture.runAsync(() -> {
			try {
				// Build event data
				Map<String, Object> eventData = new HashMap<>();
				eventData.put("type", type);
				eventData.put("message", message);
				
				if (additionalData != null) {
					eventData.putAll(additionalData);
				}
				
				// Convert to JSON
				JSONObject jsonPayload = new JSONObject(eventData);
				String jsonString = jsonPayload.toString();
				
				// Send HTTP POST request
				URL url = new URL(EMIT_EVENT_ENDPOINT);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				conn.setDoOutput(true);
				conn.setConnectTimeout(TIMEOUT_MS);
				conn.setReadTimeout(TIMEOUT_MS);
				
				// Write payload
				try (OutputStream os = conn.getOutputStream()) {
					byte[] input = jsonString.getBytes(StandardCharsets.UTF_8);
					os.write(input, 0, input.length);
				}
				
				// Get response code (but don't process response body)
				int responseCode = conn.getResponseCode();
				
				// Only log if there's an unexpected error
				if (responseCode >= 400) {
					System.err.println("Event emission failed with status: " + responseCode);
				}
				
				conn.disconnect();
				
			} catch (Exception e) {
				// Silently fail - don't let event emission errors affect workflow
				// Uncomment for debugging: System.err.println("Failed to emit event: " + e.getMessage());
			}
		});
	}

	/**
	 * Emit an event with just type and message.
	 * 
	 * @param type The event type
	 * @param message The event message
	 */
	public static void emitEvent(String type, String message) {
		emitEvent(type, message, null);
	}
}
