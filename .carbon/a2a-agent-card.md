```java
public AgentCard agentCard() {
    return AgentCard.builder()
            .name("Riot Games Support Agent")
            .description("Handles billing inquiries, refunds, and account issues for Riot Games.")
            .supportedInterfaces(List.of(
                    new AgentInterface(TransportProtocol.JSONRPC.asString(), AGENT_JSONRPC_URL)))
            .version("1.0.0")
            .capabilities(AgentCapabilities.builder()
                    .streaming(true)
                    .pushNotifications(false)
                    .build())
            .defaultInputModes(Collections.singletonList("text"))
            .defaultOutputModes(Collections.singletonList("text"))
            .skills(Collections.singletonList(AgentSkill.builder()
                    .id("billing")
                    .name("Billing Support")
                    .description("Refunds, duplicate charges, payment issues")
                    .tags(Collections.singletonList("billing"))
                    .build()))
            .build();
}
```

```ts
const agentCard: AgentCard = {
  name: "Riot Games Support Agent",
  description:
    "Handles billing inquiries, refunds, and account issues for Riot Games.",
  protocolVersion: "0.3.0",
  url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`,
  skills: [
    {
      id: "billing",
      name: "Billing Support",
      description: "Refunds, duplicate charges, payment issues",
    },
    {
      id: "account",
      name: "Account Support",
      description: "Account verification, password resets",
    },
  ],
  defaultInputModes: ["text"],
  defaultOutputModes: ["text", "data"],
  additionalInterfaces: [
    { url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`, transport: "JSONRPC" },
    { url: `http://${HOST}:${HTTP_PORT}/a2a/rest`, transport: "HTTP+JSON" },
    { url: `${HOST}:${GRPC_PORT}`, transport: "GRPC" },
  ],
};
```
