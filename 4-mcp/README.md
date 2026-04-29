# Exercise 4 - MCP

## Goals

The goal of this exercise is to understand how to use Model Context Protocol (MCP) to dynamically load Resources, Prompts, and Tools from a MCP Server.

Integrating an MCP Client into your application allows the LLM to access a wide range of additional capabilities using an industry-standard protocol.

All of the general concepts of Tool Calling, as described in the previous exercise, still apply. The main difference is that instead of defining the tools and prompts directly in your code, you can load them dynamically from an MCP Server.

## What you need to know

The Model Context Protocol (MCP) is an open standard developed by Anthropic that enables large language models (LLMs) to access external tools and data sources through a standardized, two-way connection. It acts as a universal translator, allowing LLMs to interact with diverse systems, like databases, APIs, and other services, regardless of their underlying technology. MCP follows a client-server architecture, where clients (LLM applications) connect to servers (which expose specific tools and data) through a well-defined protocol.

MCP has been adopted by most of the major LLM providers, such as OpenAI, Anthropic, and Microsoft.

### How it works

MCP uses a client-server model. AI applications (clients) connect to servers that expose specific capabilities. Instead of needing custom code for each API or data source, MCP provides a standardized interface (built on JSON-RPC 2.0) for communication. Clients and servers establish a handshake, exchanging information about their capabilities and protocol versions. The client can then discover what the server offers (tools, resources, prompts).

If the AI determines it needs to use a tool, it sends an invocation request to the appropriate server. The server executes the requested action (e.g., fetching data from a database or calling an API) and sends the result back to the client. The client relays the result back to the AI application, allowing it to incorporate the fresh, external information into its context and generate a response.

### Client-Server Model

MCP follows a client-server architecture where:

- Hosts are LLM applications (like Claude Desktop or IDEs) that initiate connections
- Clients maintain 1:1 connections with servers, inside the host application
- Servers provide context, tools, and prompts to clients

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.10.0</version>
</dependency>
```

The most useful MCP client transport that we will focus on is the `HttpClientSseClientTransport` this transport uses Server-Sent Events (SSE) to establish a persistent connection to the MCP server, allowing for real-time updates and interactions. This is particularly useful for applications that require continuous data streams or frequent updates from the server.

There are other transports defined as part of the MCP specification, including:

`STDIO` for spawning a process (locally) that can handle MCP requests, allowing for local execution of tools and prompts without needing a separate server.

`Streamable HTTP` which uses HTTP POST requests for client-to-server communication and optional Server-Sent Events (SSE) streams for server-to-client communication. At the time of writing, this transport is not yet implemented in the Java MCP SDK.

```java
Config config = new Config();
String MCP_SERVER_BASE_URL = config.getProperty("MCP_SERVER_BASE_URL");
String MCP_SERVER_SSE_URL = config.getProperty("MCP_SERVER_SSE_URL");

// Create McpClientTransport using HttpClientSseClientTransport
HttpClientSseClientTransport transport = HttpClientSseClientTransport
        .builder(MCP_SERVER_BASE_URL)
        .sseEndpoint(MCP_SERVER_SSE_URL)
        .build();

// Create a sync client with custom configuration
McpSyncClient client = McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(30))
        .build();

client.initialize();

// List available tools
ListToolsResult tools = client.listTools();
```

### Transport Options

Originally MCP supported STDIO and Server Side Events based data transport. One of the biggest changes, recently, to the protocol has been the deprecation of the SSE transport and the addition of a new Streamable HTTP transport.

We’re going to talk about these transports quickly just to be aware that there are a few different options for implementing communication between the client and server.

The other reason to talk about these options is that the Java SDK, at the time of writing this, did not yet support the Streamable HTTP transport.

These were some positives to using HTTP+SSE transport:

- Streaming large results can be done immediately, using the existing SSE connection
- Event-driven triggers, the server can notify clients about changes, alerts, status updates
- Simplicity, uses standard HTTP requiring no special protocols or complex setup

However there are some negatives:

- Unidirectional, data can only flow from the server to the client on the persistent connection
- Long-lived connections use a lot of resources, especially at larger scales

These negatives brought about the introduction of Streamable HTTP. Positives of Streamable HTTP:

- Stateless servers are supported, removes the need for the long-lived connections.
- Plain HTTP, making it compatible with common HTTP middleware, HTTP proxies, and hosting platforms
- Optional streaming for backwards compatibility by upgrading to SSE when needed
- Extremely scalable

### MCP Server

Like any other server or service we consume, the MCP server that we integrate with might be one we create ourselves or it could be provided by a third party that we’re simply integrating with.

The MCP specification provides a reference implementation of an MCP Server in a bunch of different languages including C#, Java, Kotlin, Python, TypeScript, among others.

To provide a basic example of an MCP server we have provided a simple implementation, in TypeScript, that exposes a basic Weather tool. This server is running locally on port 8090 as part of the Docker Compose setup.
The MCP specification is implemented in a number of different languages and frameworks, including Java/Spring Boot. For more information you can refer to the MCP Java SDK: <https://github.com/modelcontextprotocol/java-sdk>

From our TypeScript example, and for most of the other SDKs, the pattern is the same. You define an MCP Server instance that exposes a set of tools and resources. The MCP Server is wrapped in some kind of transport, such as HTTP or SSE, to allow clients to connect and interact with it.

We can take advantage of existing frameworks such as Spring Boot in Java or Express in TypeScript to create the HTTP endpoints.

```ts
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";

const server = new McpServer({
  name: "mcp-server",
  version: "1.0.0",
});

server.registerTool(
  "weather-by-zip-code",
  {
    title: "Weather by Zip Code",
    description: "Get current weather for a zip code",
    inputSchema: { zipCode: z.string() },
  },
  async ({ zipCode }) => {
    const weatherData = {
      temperature: 72,
      condition: "Sunny",
    };
    return weatherData;
  },
);
```

If we're using Spring Boot we can use the MCP Server Boot Starter to simplify the setup. This starter provides auto-configuration for setting up an MCP server in your Spring Boot application.

See: <https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html>

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

```java
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

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
