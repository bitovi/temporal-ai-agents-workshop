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

public class ListEvents {

	public static void main(String[] args) throws Exception {
		Config config = new Config();

		ListEventsResponse response = AgentCoreMemory.listEvents();
		
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
