package bitovi.activities.a2a;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.a2a.client.Client;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.http.A2ACardResolver;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.AgentCard;

public class A2ARegistry {
    // Cache: agentUrl -> resolved client + card
    public static final Map<String, AgentConnection> connections = new ConcurrentHashMap<>();

    public record AgentConnection(Client client, AgentCard card, AtomicReference<Boolean> discoveryEmitted) {
        public AgentConnection(Client client, AgentCard card) {
            this(client, card, new AtomicReference<>(false));
        }
    }

    // ── Client lifecycle ─────────────────────────────────────────────────

    public static AgentConnection getOrCreateConnection(String agentUrl) throws Exception {
        AgentConnection existing = connections.get(agentUrl);
        if (existing != null)
            return existing;

        System.out.println("[A2ATool] Resolving agent card at " + agentUrl);
        AgentCard card = new A2ACardResolver(agentUrl).getAgentCard();
        System.out.println("[A2ATool] Discovered agent: " + card.name());

        ClientConfig clientConfig = new ClientConfig.Builder()
                .setAcceptedOutputModes(List.of("text", "data"))
                .build();
        Client client = Client.builder(card)
                .clientConfig(clientConfig)
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                .build();

        AgentConnection conn = new AgentConnection(client, card);
        connections.put(agentUrl, conn);
        return conn;
    }
}
