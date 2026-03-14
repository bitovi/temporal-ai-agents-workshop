package bitovi.activities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import io.a2a.client.http.A2ACardResolver;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;

import bitovi.common.EventClient;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

/**
 * A tool that searches an agent registry to find remote A2A agents by keyword.
 *
 * In production this would call a real registry web service. For this demo,
 * it uses a hardcoded in-memory list of known agents that simulates the concept.
 */
public class AgentRegistryTool {
    private static final Gson gson = new Gson();

    /** An entry in the agent registry. */
    public record AgentEntry(
        String name,
        String url,
        String description,
        List<String> tags
    ) {}

    /** The mock registry — in production this would be a real service. */
    private static final List<AgentEntry> REGISTRY = List.of(
        new AgentEntry(
            "Riot Games Support Agent",
            "http://localhost:4000",
            "Handles billing inquiries, refunds, and account issues for Riot Games.",
            List.of("support", "billing", "refunds", "account", "riot games", "gaming")
        ),
        new AgentEntry(
            "Book Agent",
            "http://localhost:5001",
            "An agent that can answer questions about books, recommend reading, and search the Gutenberg library.",
            List.of("books", "reading", "literature", "gutenberg", "library")
        )
    );

    /**
     * Search the registry for agents matching a query string.
     * Matches against name, description, and tags (case-insensitive).
     */
    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        Map<String, Object> params = toolUseInput;
        if (toolUseInput.containsKey("map") && toolUseInput.get("map") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) toolUseInput.get("map");
            params = nested;
        }

        if (params == null || !params.containsKey("query")) {
            throw new IllegalArgumentException("Invalid input: 'query' is required.");
        }

        String query = params.get("query").toString().toLowerCase();
        System.out.println("[AgentRegistryTool] Searching for: " + query);

        List<Map<String, Object>> results = new ArrayList<>();
        for (AgentEntry agent : REGISTRY) {
            if (matches(agent, query)) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("name", agent.name());
                entry.put("url", agent.url());
                entry.put("description", agent.description());
                entry.put("tags", agent.tags());
                results.add(entry);

                emitDiscovery(agent);
            }
        }

        System.out.println("[AgentRegistryTool] Found " + results.size() + " matching agent(s)");
        return gson.toJson(Map.of("agents", results));
    }

    /**
     * Resolve the agent card and emit an a2a_discovery event for the UI.
     * If the card can't be resolved (agent is down), emits with registry data only.
     */
    private static void emitDiscovery(AgentEntry agent) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", agent.name());
        data.put("description", agent.description());

        try {
            AgentCard card = new A2ACardResolver(agent.url()).getAgentCard();
            if (card.skills() != null) {
                List<Map<String, String>> skillsList = new ArrayList<>();
                for (AgentSkill skill : card.skills()) {
                    Map<String, String> s = new HashMap<>();
                    s.put("id", skill.id());
                    s.put("name", skill.name());
                    s.put("description", skill.description());
                    skillsList.add(s);
                }
                data.put("skills", skillsList);
            }
        } catch (Exception e) {
            System.err.println("[AgentRegistryTool] Could not resolve agent card for " + agent.name() + ": " + e.getMessage());
        }

        data.put("lane", EventClient.LANE_CLIENT);
        EventClient.emitEvent("a2a_discovery", agent.name() + " — " + agent.description(), data);
    }

    private static boolean matches(AgentEntry agent, String query) {
        if (agent.name().toLowerCase().contains(query)) return true;
        if (agent.description().toLowerCase().contains(query)) return true;
        for (String tag : agent.tags()) {
            if (tag.toLowerCase().contains(query) || query.contains(tag.toLowerCase())) return true;
        }
        return false;
    }

    public static Tool getBedrockTool() {
        Map<String, Document> queryProp = new HashMap<>();
        queryProp.put("type", Document.fromString("string"));
        queryProp.put("description", Document.fromString(
                "A keyword or phrase to search for (e.g. 'billing', 'books', 'support')"));

        Map<String, Document> properties = new HashMap<>();
        properties.put("query", Document.fromMap(queryProp));

        Map<String, Document> root = new HashMap<>();
        root.put("type", Document.fromString("object"));
        root.put("properties", Document.fromMap(properties));
        root.put("required", Document.fromList(List.of(Document.fromString("query"))));

        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name("search_agent_registry")
                        .description("Search a registry of remote A2A agents by keyword. "
                                + "Returns a list of agents with their name, URL, description, and tags. "
                                + "Use this to discover which agents are available before contacting one.")
                        .inputSchema(ToolInputSchema.builder()
                                .json(Document.fromMap(root))
                                .build())
                        .build())
                .build();
    }
}
