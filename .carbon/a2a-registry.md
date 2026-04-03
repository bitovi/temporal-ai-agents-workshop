```java
public class ToolRegistry {
    private static final Map<String, ToolFunction<String, ToolInput>, String>> toolExecutors = new HashMap<>();

    static {
        // A2A agent discovery and communication tools
        toolExecutors.put("search_agent_registry", AgentRegistryTool::execute);
        toolExecutors.put("a2a_send_message", A2ATool::execute);
    }

    public static List<Tool> getAllBedrockTools() {
        List<Tool> tools = new ArrayList<>();
        tools.add(AgentRegistryTool.getBedrockTool());
        tools.add(A2ATool.getBedrockTool());
        return tools;
    }
}
```

```java
public class AgentRegistryTool {
    public record AgentEntry(String name, String url, String description,List<String> tags) {}

    private static final List<AgentEntry> REGISTRY = List.of(
            new AgentEntry("Riot Games Support Agent", "http://localhost:4000",
                "Handles billing inquiries, refunds, and account issues for Riot Games.",
                List.of("support", "billing", "refunds", "account", "riot games", "gaming")));

    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        ToolInput input = validateToolInput(toolUseInput);
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
}
```

```java
public class A2ATool {
    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        A2ARequestInput params = A2AHelpers.validateToolParams(toolUseInput);
        Message message = params.existingTask()
            ? A2A.createUserTextMessage(params.message(), params.contextId(),params.taskId())
            : A2A.createUserTextMessage(params.message(), null, null);
        AgentConnection conn = getOrCreateConnection(params.agentUrl());
        return A2AHandler.sendAndCollect(conn, message);
    }

    public static AgentConnection getOrCreateConnection(String agentUrl) throws Exception {
        AgentConnection existing = connections.get(agentUrl);
        if (existing != null) return existing;

        AgentCard card = new A2ACardResolver(agentUrl).getAgentCard();

        A2AClient client = A2AClient.builder(card).build();
        AgentConnection conn = new AgentConnection(client, card);
        connections.put(agentUrl, conn);
        return conn;
    }
}
```
