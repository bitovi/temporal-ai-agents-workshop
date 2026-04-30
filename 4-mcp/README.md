# Exercise 4 - MCP

## Goals

Learn how to use the Model Context Protocol (MCP) to dynamically load Resources, Prompts, and Tools from an MCP Server. Integrating an MCP Client lets your LLM application access a wide range of capabilities through an industry-standard protocol.

The general Tool Calling concepts from the previous exercise still apply—the difference is that tools and prompts are loaded dynamically from a server instead of being hard-coded.

## What you need to know

The Model Context Protocol (MCP) is an open standard, originally developed by Anthropic and now adopted by most major LLM providers (OpenAI, Anthropic, Microsoft). It acts as a universal translator between LLMs and external systems—databases, APIs, and other services—using a standardized two-way connection built on JSON-RPC 2.0.

### Client-Server Model

MCP follows a client-server architecture:

- **Hosts** are LLM applications (e.g., Claude Desktop, IDEs) that initiate connections
- **Clients** maintain 1:1 connections with servers, inside the host application
- **Servers** expose tools, resources, and prompts to clients

After a handshake exchanging capabilities and protocol versions, the client discovers what the server offers. When the model decides to invoke a tool, the client sends the request to the server, the server executes it, and the result flows back into the model's context.

### Transport Options

MCP originally supported two transports: **STDIO** (spawning a local process) and **HTTP + Server-Sent Events (SSE)**. SSE has since been deprecated in favor of **Streamable HTTP**, though the Java SDK does not yet implement Streamable HTTP at the time of writing.

HTTP + SSE pros: instant streaming of large results, server-driven event push, and simple standard HTTP. Cons: unidirectional on the persistent connection, and long-lived connections don't scale well.

Streamable HTTP pros: supports stateless servers, plain HTTP (compatible with middleware/proxies/hosting), optional SSE upgrade for streaming, and far better scalability.

### MCP Client (Java)

This exercise uses `HttpClientSseClientTransport`, which keeps a persistent SSE connection for real-time updates.

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.10.0</version>
</dependency>
```

```java
Config config = new Config();
String MCP_SERVER_BASE_URL = config.getProperty("MCP_SERVER_BASE_URL");
String MCP_SERVER_SSE_URL = config.getProperty("MCP_SERVER_SSE_URL");

HttpClientSseClientTransport transport = HttpClientSseClientTransport
        .builder(MCP_SERVER_BASE_URL)
        .sseEndpoint(MCP_SERVER_SSE_URL)
        .build();

McpSyncClient client = McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(30))
        .build();

client.initialize();
ListToolsResult tools = client.listTools();
```

### MCP Server

MCP servers may be third-party or built in-house. The specification has reference implementations in C#, Java, Kotlin, Python, TypeScript, and others (see the [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)).

For this exercise, a simple TypeScript MCP server exposing a Weather tool runs locally on port 8090 via Docker Compose:

```ts
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

const server = new McpServer({ name: "mcp-server", version: "1.0.0" });

server.registerTool(
  "weather-by-zip-code",
  {
    title: "Weather by Zip Code",
    description: "Get current weather for a zip code",
    inputSchema: { zipCode: z.string() },
  },
  async ({ zipCode }) => ({ temperature: 72, condition: "Sunny" }),
);
```

In Java, the [MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) provides auto-configuration for Spring Boot:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

```java
@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService) {
        return MethodToolCallbackProvider.builder().toolObjects(weatherService).build();
    }
}
```
