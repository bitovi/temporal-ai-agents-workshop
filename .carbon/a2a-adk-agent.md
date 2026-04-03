```java
// 1. Resolve the public AgentCard from the remote agents endpoint
AgentCard publicAgentCard = new A2ACardResolver(
    "http://localhost:9090", "http://localhost:9090/.well-known/agent-card.json"
).getAgentCard();

// 2. Build the official A2A SDK Client using the resolved card and transport
Client a2aClient = Client.builder(publicAgentCard)
    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
    .clientConfig(
        new ClientConfig.Builder().setStreaming(publicAgentCard.capabilities().streaming()).build()
    ).build();

// 3. Wrap it in the ADK's RemoteA2AAgent natively
BaseAgent remotePrimeAgent = RemoteA2AAgent.builder()
    .name(publicAgentCard.name())
    .a2aClient(a2aClient)
    .agentCard(publicAgentCard)
    .build();
```
