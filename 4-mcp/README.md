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

### MCP Client

The MCP Client is responsible for initiating requests to the MCP Server and handling responses. It abstracts the details of the MCP protocol, allowing developers to interact with external tools and data sources seamlessly. The client manages the connection to the server, including authentication, request formatting, and response parsing.

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
HttpClientSseClientTransport transport = HttpClientSseClientTransport
        .builder("https://api.repkam09.com/api/mcp")
        .sseEndpoint("https://api.repkam09.com/api/mcp")
        .build();

// Create a sync client with custom configuration
McpSyncClient client = McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(10))
        .build();

// Initialize connection
client.initialize();

// List available tools
ListToolsResult tools = client.listTools();
```

### MCP Server

To provide a basic example of an MCP server we will look at a simple Java Spring Boot application that exposes a single tool for fetching weather data. Spring Boot provides a set of `ai` imports that make it easy to create MCP servers.

```java
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

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

## Solution
