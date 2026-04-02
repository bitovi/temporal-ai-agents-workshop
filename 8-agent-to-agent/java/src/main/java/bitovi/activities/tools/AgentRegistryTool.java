package bitovi.activities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class AgentRegistryTool {
    private static final Gson gson = new Gson();

    public record AgentEntry(
            String name,
            String url,
            String description,
            List<String> tags) {
    }

    /** The mock registry — in production this would be a real service. */
    private static final List<AgentEntry> REGISTRY = List.of(
            new AgentEntry(
                    "Riot Games Support Agent",
                    "http://localhost:4000",
                    "Handles billing inquiries, refunds, and account issues for Riot Games.",
                    List.of("support", "billing", "refunds", "account", "riot games", "gaming")),
            new AgentEntry(
                    "Travel Planner Agent",
                    "http://localhost:6001",
                    "Plans trips, searches flights and hotels, builds itineraries, and provides destination recommendations.",
                    List.of("travel", "flights", "hotels", "itinerary", "vacation", "booking", "destinations")),
            new AgentEntry(
                    "Code Review Agent",
                    "http://localhost:6002",
                    "Reviews pull requests, identifies bugs and security issues, suggests improvements, and enforces coding standards.",
                    List.of("code review", "pull request", "bugs", "security", "linting", "engineering", "software")),
            new AgentEntry(
                    "Finance Agent",
                    "http://localhost:6003",
                    "Tracks expenses, analyzes budgets, provides investment summaries, and generates financial reports.",
                    List.of("finance", "budget", "expenses", "investments", "reports", "accounting", "money")),
            new AgentEntry(
                    "Calendar & Scheduling Agent",
                    "http://localhost:6004",
                    "Manages calendars, schedules meetings across time zones, resolves conflicts, and sends reminders.",
                    List.of("calendar", "scheduling", "meetings", "time zones", "reminders", "availability")),
            new AgentEntry(
                    "Research Agent",
                    "http://localhost:6005",
                    "Conducts deep research on topics, summarizes academic papers, and compiles citation-backed reports.",
                    List.of("research", "papers", "academic", "citations", "summarization", "knowledge")));

    private static ToolInput validateToolInput(Map<String, Object> toolUseInput) {
        if (toolUseInput.containsKey("map") && toolUseInput.get("map") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nested = (Map<String, Object>) toolUseInput.get("map");
            if (nested.containsKey("query") && nested.get("query") != null) {
                return new ToolInput(nested.get("query").toString());
            }
        }

        return new ToolInput(null);
    }

    private record ToolInput(String query, boolean listAll) {
        public ToolInput(String query) {
            this(query, query == null || query.trim().isEmpty());
        }
    }

    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        ToolInput input = validateToolInput(toolUseInput);

        if (input.listAll()) {
            System.out.println("[AgentRegistryTool] Listing all agents");
        } else {
            System.out.println("[AgentRegistryTool] Searching for: " + input.query());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (AgentEntry agent : REGISTRY) {
            if (matches(agent, input)) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("name", agent.name());
                entry.put("url", agent.url());
                entry.put("description", agent.description());
                entry.put("tags", agent.tags());
                results.add(entry);
            }
        }

        System.out.println("[AgentRegistryTool] Found " + results.size() + " agent(s)");
        return gson.toJson(Map.of("agents", results));
    }

    private static boolean matches(AgentEntry agent, ToolInput input) {
        if (input.listAll()) {
            return true;
        }

        String query = input.query().toLowerCase();

        // Match against the full query first
        String nameLower = agent.name().toLowerCase();
        String descLower = agent.description().toLowerCase();
        if (nameLower.contains(query)) {
            return true;
        }

        if (descLower.contains(query)) {
            return true;
        }

        for (String tag : agent.tags()) {
            if (tag.toLowerCase().contains(query) || query.contains(tag.toLowerCase())) {
                return true;
            }
        }

        // Also match if ANY individual word in the query matches
        String[] words = query.split("\\s+");
        for (String word : words) {
            if (word.length() < 2)
                continue; // skip tiny words
            if (nameLower.contains(word))
                return true;
            if (descLower.contains(word))
                return true;
            for (String tag : agent.tags()) {
                if (tag.toLowerCase().contains(word))
                    return true;
            }
        }
        return false;
    }

    public static Tool getBedrockTool() {
        Map<String, Document> queryProp = new HashMap<>();
        queryProp.put("type", Document.fromString("string"));
        queryProp.put("description", Document.fromString(
                "Optional keyword or phrase to filter agents (e.g. 'billing', 'books', 'support'). "
                        + "Omit or leave empty to list all available agents."));

        Map<String, Document> properties = new HashMap<>();
        properties.put("query", Document.fromMap(queryProp));

        Map<String, Document> root = new HashMap<>();
        root.put("type", Document.fromString("object"));
        root.put("properties", Document.fromMap(properties));

        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name("search_agent_registry")
                        .description("Search a registry of remote A2A agents by keyword, or list all available agents. "
                                + "Returns agents with their name, URL, description, and tags. "
                                + "Call with no query to browse all agents, or with a query to filter by keyword. "
                                + "Use this to discover which agents are available before contacting one.")
                        .inputSchema(ToolInputSchema.builder()
                                .json(Document.fromMap(root))
                                .build())
                        .build())
                .build();
    }
}
