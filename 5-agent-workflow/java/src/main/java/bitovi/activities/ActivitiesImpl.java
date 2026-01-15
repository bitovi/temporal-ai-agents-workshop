package bitovi.activities;

import java.util.List;

import org.json.JSONObject;

import bitovi.UsageMetadata;
import bitovi.activities.DTO.CompactResponse;
import bitovi.activities.DTO.ObservationResponse;
import bitovi.activities.DTO.PersistMessage;
import bitovi.activities.DTO.ThoughtResponse;
import io.temporal.failure.ApplicationFailure;

public class ActivitiesImpl implements Activities {

	@Override
	public ThoughtResponse thoughtEntity(List<String> context) throws ApplicationFailure {
		try {
			System.out.println("thoughtEntity called with context size: " + context.size());
			
			// Return a realistic stubbed answer response
			return new ThoughtResponse(
				"answer",
				null,
				"I'm thinking about your question...",
				null,
				new UsageMetadata(50, 30, 80)
			);
		} catch (Exception e) {
			System.err.println("Error in thoughtEntity: " + e.getMessage());
			throw ApplicationFailure.newFailure("thoughtEntity failed: " + e.getMessage(), "ThoughtEntityError");
		}
	}

	@Override
	public String actionEntity(String toolName, Object input) throws ApplicationFailure {
		try {
			System.out.println("actionEntity called with tool: " + toolName);
			
			// Return a simple JSON string result
			JSONObject jsonResult = new JSONObject();
			jsonResult.put("status", "success");
			jsonResult.put("result", "mock result");
			return jsonResult.toString();
		} catch (Exception e) {
			System.err.println("Error in actionEntity: " + e.getMessage());
			throw ApplicationFailure.newFailure("actionEntity failed: " + e.getMessage(), "ActionEntityError");
		}
	}

	@Override
	public ObservationResponse observationEntity(List<String> context, String actionResult) throws ApplicationFailure {
		try {
			System.out.println("observationEntity called with action result: " + actionResult);
			
			return new ObservationResponse(
				"I observed the action completed successfully with result: " + actionResult,
				new UsageMetadata(40, 25, 65)
			);
		} catch (Exception e) {
			System.err.println("Error in observationEntity: " + e.getMessage());
			throw ApplicationFailure.newFailure("observationEntity failed: " + e.getMessage(), "ObservationEntityError");
		}
	}

	@Override
	public CompactResponse compactEntity(List<String> context) throws ApplicationFailure {
		try {
			System.out.println("compactEntity called with context size: " + context.size());
			
			// Return the original context without actual compaction
			return new CompactResponse(
				context,
				new UsageMetadata(100, 50, 150)
			);
		} catch (Exception e) {
			System.err.println("Error in compactEntity: " + e.getMessage());
			throw ApplicationFailure.newFailure("compactEntity failed: " + e.getMessage(), "CompactEntityError");
		}
	}

	@Override
	public void persistEntity(List<PersistMessage> messages) throws ApplicationFailure {
		try {
			System.out.println("persistEntity called with " + messages.size() + " messages:");
			for (PersistMessage msg : messages) {
				System.out.println("  - Role: " + msg.role() + ", Message: " + msg.message());
			}
		} catch (Exception e) {
			System.err.println("Error in persistEntity: " + e.getMessage());
			throw ApplicationFailure.newFailure("persistEntity failed: " + e.getMessage(), "PersistEntityError");
		}
	}

	// Helper class for JSON serialization
	private static class ActionResult {
		public String status;
		public String data;

		public ActionResult(String status, String data) {
			this.status = status;
			this.data = data;
		}
	}
}
