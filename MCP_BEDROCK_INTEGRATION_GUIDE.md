# AWS Bedrock Runtime + MCP Server Tools Integration Guide

This guide demonstrates how to integrate AWS Bedrock Runtime models with custom MCP server tools in a Java-based AI agent system using Temporal workflows.

## Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   User Query    │───▶│  Temporal        │───▶│   AWS Bedrock   │
│                 │    │  Workflow        │    │   Runtime       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │                         │
                              │                         │
                              ▼                         ▼
                       ┌──────────────────┐    ┌─────────────────┐
                       │  MCP Tool        │    │  Tool Execution │
                       │  Integration     │    │  (Local + MCP)  │
                       └──────────────────┘    └─────────────────┘
```

## Key Components

### 1. MCPToolIntegration Class
- **Purpose**: Bridges MCP server tools with AWS Bedrock Runtime models
- **Location**: `src/main/java/bitovi/providers/MCPToolIntegration.java`
- **Features**:
  - Connects to MCP servers via HTTP SSE transport
  - Converts MCP tool definitions to Bedrock-compatible format
  - Executes MCP tools and returns results

### 2. Enhanced BedrockProvider
- **Purpose**: Extended AWS Bedrock provider with MCP tool support
- **Location**: `src/main/java/bitovi/providers/BedrockProvider.java`
- **Features**:
  - `chatWithAllTools()` method that combines local and MCP tools
  - Automatic tool routing (local vs MCP)
  - Error handling and fallback mechanisms

### 3. MCPBedrockActivities
- **Purpose**: Temporal activities for MCP-Bedrock integration
- **Location**: `src/main/java/bitovi/activities/MCPBedrockActivities.java`
- **Methods**:
  - `processWithMCPTools(String userQuery)`: Process queries using both tool types
  - `getAvailableMCPTools()`: List available MCP tools
  - `executeSpecificMCPTool(String toolName, String input)`: Execute specific MCP tools

### 4. MCPBedrockWorkflow
- **Purpose**: Temporal workflow orchestrating the integration
- **Location**: `src/main/java/bitovi/workflows/MCPBedrock/MCPBedrockWorkflow.java`
- **Features**:
  - Logging of available tools
  - Error handling and workflow management
  - Query processing coordination

## Implementation Example

### Basic Usage

```java
// Initialize the integration
MCPToolIntegration mcpIntegration = new MCPToolIntegration();
BedrockProvider bedrockProvider = new BedrockProvider();

// Process a query that might use MCP tools
ArrayList<LLMProviderChatMessage> messages = new ArrayList<>();
messages.add(new LLMProviderChatMessage("user", "What's the weather in San Francisco?"));

LLMProviderChatMessage response = bedrockProvider.chatWithAllTools(messages);
System.out.println("Response: " + response.getContent());
```

### Temporal Workflow Usage

```java
// Run via Temporal workflow
WorkflowClient client = WorkflowClient.newInstance(service);
MCPBedrockWorkflow workflow = client.newWorkflowStub(MCPBedrockWorkflow.class, options);

String result = workflow.processQueryWithMCPTools("Calculate the cosine of 1.57 and get weather for NYC");
```

## Configuration

### Required Environment Variables

```properties
# AWS Bedrock Configuration
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key
AWS_SESSION_TOKEN=your-session-token
AWS_MODEL_ARN=arn:aws:bedrock:us-east-2:account:inference-profile/model-id
AWS_MODEL_ID=your-model-id

# MCP Server Configuration
LIFEFORCE_MCP_TOKEN=your-mcp-token
```

### MCP Server Setup

1. **Authentication**: Configure Bearer token authentication
2. **Transport**: Uses HTTP Server-Sent Events (SSE) transport
3. **Endpoint**: Default endpoint `https://api.repkam09.com/api/mcp`

## Tool Integration Flow

### 1. Tool Discovery
```java
// MCP tools are discovered at initialization
ListToolsResult tools = mcpClient.listTools();
this.availableTools = tools.tools();
```

### 2. Tool Specification Conversion
```java
// MCP tools are converted to Bedrock format
public List<Tool> getBedrockToolSpecifications() {
    // Convert each MCP tool to Bedrock Tool specification
    // Include schema mapping and validation
}
```

### 3. Tool Execution
```java
// Bedrock determines which tool to call
// Local tools: executed directly
// MCP tools: routed to MCP server
switch (toolUseBlock.name()) {
    case "calculate_cosine":
        // Local tool execution
        break;
    default:
        // Try MCP tool execution
        result = mcpIntegration.executeMCPTool(toolName, arguments);
}
```

## Error Handling

### Connection Failures
```java
try {
    this.mcpIntegration = new MCPToolIntegration();
} catch (Exception e) {
    System.err.println("Failed to initialize MCP integration: " + e.getMessage());
    this.mcpIntegration = null;
}
```

### Tool Execution Errors
```java
try {
    result = mcpIntegration.executeMCPTool(toolName, arguments);
} catch (Exception e) {
    result = "Error executing MCP tool: " + e.getMessage();
}
```

## Benefits

### 1. **Hybrid Tool Ecosystem**
- Combines local tools (fast, reliable) with remote MCP tools (dynamic, extensive)
- Automatic failover and error handling

### 2. **Standardized Integration**
- Uses MCP standard for consistent tool integration
- Minimal code changes to add new tools

### 3. **Scalable Architecture**
- Temporal workflows provide reliability and scalability
- Asynchronous processing capabilities

### 4. **AWS Bedrock Integration**
- Leverages AWS's managed LLM infrastructure
- Automatic tool calling and conversation management

## Running the Demo

1. **Start Temporal Server**:
   ```bash
   docker-compose up temporal
   ```

2. **Run the Worker**:
   ```bash
   ./run-worker.sh
   ```

3. **Run the Demo** (when fully implemented):
   ```bash
   ./run-mcp-bedrock-demo.sh
   ```

## Next Steps

### 1. **Complete Implementation**
- Finish the MCPBedrockActivitiesImpl integration
- Add proper error handling and logging
- Implement tool result parsing

### 2. **Enhanced Tool Support**
- Add more local tools
- Implement custom MCP servers
- Add tool composition capabilities

### 3. **Production Readiness**
- Add monitoring and observability
- Implement rate limiting and quotas
- Add security and authentication layers

## Troubleshooting

### Common Issues

1. **MCP Connection Failures**
   - Check network connectivity
   - Verify authentication tokens
   - Ensure MCP server is running

2. **Tool Execution Errors**
   - Validate tool arguments
   - Check tool availability
   - Review error logs

3. **Bedrock Integration Issues**
   - Verify AWS credentials
   - Check model permissions
   - Validate tool specifications

This integration provides a powerful foundation for building AI agents that can leverage both local computational tools and remote web services through the standardized MCP protocol.
