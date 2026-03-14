package bitovi.common;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.json.JSONObject;

import io.temporal.activity.Activity;

/**
 * EventClient sends events to the agent-chat-server via HTTP POST requests.
 * This enables real-time updates to be sent to the web UI through Server-Sent Events (SSE).
 * 
 * Events are sent asynchronously in a fire-and-forget manner - failures are silently ignored
 * to prevent activity errors from blocking workflow execution.
 */
public class EventClient {
	private static final String SERVER_URL = new Config().getProperty("AGENT_CHAT_SERVER_BASE_URL");
	private static final String EMIT_EVENT_ENDPOINT = SERVER_URL + "/api/emit-event";
	private static final int TIMEOUT_MS = 5000;
	private static long sequenceCounter = 0;

	// Swimlane constants
	public static final String LANE_USER = "user";
	public static final String LANE_CLIENT = "client";
	public static final String LANE_REMOTE = "remote";

	/**
	 * Emit an event to the server asynchronously.
	 * 
	 * @param type The event type (e.g., "thought", "answer", "action", "observation", "error")
	 * @param message The event message content
	 * @param additionalData Optional additional data to include in the event
	 */
	public static void emitEvent(String type, String message, Map<String, Object> additionalData) {
		// Capture timestamp, sequence, and workflowId BEFORE async execution to maintain order
		final long timestamp = System.currentTimeMillis();
		final long sequence = getNextSequence();
		String capturedWorkflowId = null;
		try {
			capturedWorkflowId = Activity.getExecutionContext().getInfo().getWorkflowId();
		} catch (Exception ignored) {
			// Not in an activity context
		}
		final String workflowId = capturedWorkflowId;
		
		CompletableFuture.runAsync(() -> {
			try {
				// Build event data
				Map<String, Object> eventData = new HashMap<>();
				eventData.put("type", type);
				eventData.put("message", message);
				eventData.put("timestamp", timestamp);
				eventData.put("sequence", sequence);
				if (workflowId != null) {
					eventData.put("workflowId", workflowId);
				}
				
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

	/**
	 * Emit an event with lane metadata for the swimlane UI.
	 * 
	 * @param type       The event type
	 * @param message    The event message
	 * @param lane       The swimlane this event belongs to ("user", "client", "remote")
	 * @param targetLane Optional target lane for directional events (null if not directional)
	 */
	public static void emitEvent(String type, String message, String lane, String targetLane) {
		Map<String, Object> data = new HashMap<>();
		data.put("lane", lane);
		if (targetLane != null) {
			data.put("targetLane", targetLane);
		}
		emitEvent(type, message, data);
	}

	/**
	 * Emit an event with lane metadata and additional data.
	 * 
	 * @param type           The event type
	 * @param message        The event message
	 * @param lane           The swimlane this event belongs to
	 * @param targetLane     Optional target lane for directional events
	 * @param additionalData Extra fields to include
	 */
	public static void emitEvent(String type, String message, String lane, String targetLane, Map<String, Object> additionalData) {
		Map<String, Object> data = new HashMap<>();
		data.put("lane", lane);
		if (targetLane != null) {
			data.put("targetLane", targetLane);
		}
		if (additionalData != null) {
			data.putAll(additionalData);
		}
		emitEvent(type, message, data);
	}
	
	/**
	 * Get next sequence number in a thread-safe manner.
	 */
	private static synchronized long getNextSequence() {
		return sequenceCounter++;
	}
}
