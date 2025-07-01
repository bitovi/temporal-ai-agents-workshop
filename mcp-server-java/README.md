# MCP Server Java Demo

This is a simple implementation of a Model Context Protocol (MCP) server using Java Spring Boot. The MCP server provides several tools that can be used by AI agents.

## Tools Provided

### 1. Calculator Tool

A basic calculator that performs the following operations:

- ADD: Addition
- SUB: Subtraction
- MUL: Multiplication
- DIV: Division

### 2. Current Time Tool

Returns the current time.

### 3. Weather Tool

Generates a weather forecast for a provided zip code.
Note: This is a simulation and doesn't use real weather data.

## MCP Protocol Implementation

The server implements a simplified version of the Model Context Protocol. It supports the following endpoints:

- `/mcp/message` - Main JSON-RPC endpoint for MCP communication

Supported MCP methods:

- `initialize` - Returns server info and capabilities
- `tools/list` - Returns a list of available tools
- `tools/call` - Executes a tool with the provided arguments

## Running the Server

To run the server locally:

```bash
./mvnw spring-boot:run
```

The server will start on port 8080 by default.

## Example Tool Calls

### Calculator

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "calculator",
    "arguments": {
      "operator": "ADD",
      "a": 5,
      "b": 3
    }
  }
}
```

### Current Time

```json
{
  "jsonrpc": "2.0",
  "id": "2",
  "method": "tools/call",
  "params": {
    "name": "currentTime",
    "arguments": {}
  }
}
```

### Weather Forecast

```json
{
  "jsonrpc": "2.0",
  "id": "3",
  "method": "tools/call",
  "params": {
    "name": "weather",
    "arguments": {
      "zipCode": "12345"
    }
  }
}
```
