package bitovi;


import bitovi.common.Config;
import bitovi.common.aws.AgentCoreMemory;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A handy utility for peeking at the raw conversation history stored in
 * AWS Bedrock Agent Core Memory. Run this to see every message
 * that has been exchanged during the session so far, printed out in
 * chronological order.
 *
 * Just paste a Temporal workflow ID into {@code SESSION_ID} and run it using
 * the "ListEvents" run configuration!
 */
public class ListEvents {

	// copy workflow id from temporal ui
	private static String SESSION_ID = "agent-workflow-6aeb9f22-dd2f-4f76-acc8-429368a4617f";

	public static void main(String[] args) throws Exception {
		Config config = new Config();

		ListEventsResponse response = AgentCoreMemory.listEvents(SESSION_ID);
		
		if (response.events().isEmpty()) {
			System.out.println("Found 0 event(s)");
			return;
		}

		System.out.println(String.format("Found %s event(s)", response.events().size()));

		// Sort events by eventTimestamp in chronological order
		List<Event> sortedEvents = new ArrayList<>(response.events());
		sortedEvents.sort(Comparator.comparing(Event::eventTimestamp));

		for (Event event : sortedEvents) {
			for (PayloadType payloadType : event.payload()) {
				if (payloadType.type() != PayloadType.Type.CONVERSATIONAL) { continue; }
				Conversational conversational = payloadType.conversational();
				Role role = conversational.role();
				Content content = conversational.content();
				if (content.type() != Content.Type.TEXT) { continue; }
				String text = content.text();

				System.out.println(String.format("\n[%s]: %s", role, text));
			}
		}
	}
}
