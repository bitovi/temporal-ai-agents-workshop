# Integration: Exercise 8 with Book Agent Server

## Overview

Integrate the agentic AI system in Exercise 8 with the Book Agent Server using the A2A (Agent-to-Agent) Java SDK. This will enable the Exercise 8 agent to delegate book-related queries to the specialized Book Agent via tool calls.

**Key Components:**
- **Agentic AI System (Exercise 8):** `/8-agent-to-agent/java/`
- **Book Agent Server:** `/book-agent-server/`
- **Protocol:** A2A (Agent-to-Agent) via JSON-RPC transport
- **Reference Documentation:** [A2A Java SDK Client Documentation](https://raw.githubusercontent.com/a2aproject/a2a-java/refs/heads/main/README.md)

## Prerequisites

- Book Agent Server is running (via Docker Compose on `localhost:4000`)
- Exercise 8 Java project compiles successfully
- A2A Java SDK latest version (check https://github.com/a2aproject/a2a-java/releases)

## Implementation Plan

### Step 1: Add A2A Client Dependencies

**Files:** [8-agent-to-agent/java/pom.xml](../8-agent-to-agent/java/pom.xml)

Add the A2A Java SDK client dependencies to the Maven project.

**Changes:**
1. Add A2A SDK client dependency (includes JSON-RPC transport by default):
   ```xml
   <dependency>
       <groupId>io.github.a2asdk</groupId>
       <artifactId>a2a-java-sdk-client</artifactId>
       <version>[latest-version]</version>
   </dependency>
   ```
   
   **Note:** Check https://github.com/a2aproject/a2a-java/releases for the latest version number. The `io.github.a2asdk` groupId is temporary and may change in future releases.

**Note:** The project already has `org.json` dependency which is sufficient for any JSON processing needs. The A2A SDK handles its own JSON serialization internally (it may bring Jackson as a transitive dependency if needed).

**Verification:**
- Run `mvn compile` in the Exercise 8 directory
- Verify no dependency resolution errors
- Verify A2A classes are available in classpath

---

### Step 2: Create Book Agent Tool Class

**Files:** Create `8-agent-to-agent/java/src/main/java/bitovi/activities/tools/BookAgentTool.java`

Create a new tool that encapsulates A2A client communication with the Book Agent Server.

**Implementation Details:**

1. **Constants:**
   - Book Agent URL: `http://localhost:4000/a2a/jsonrpc`
   - Agent Card URL: `http://localhost:4000/.well-known/agent-card.json`
   - Timeout: 60 seconds (allow time for LLM processing)

2. **Core Methods:**
   - `getBedrockTool()`: Returns AWS Bedrock tool definition
   - `execute(String toolName, Map<String, Object> input)`: Executes book queries via A2A

3. **A2A Client Setup (Static Singleton Pattern):**
   - Create a static `Client` field to reuse across all tool calls
   - Initialize once on first use with lazy initialization
   - Use `A2ACardResolver` to fetch agent card: `new A2ACardResolver("http://localhost:4000").getAgentCard()`
   - Extract description and capabilities from the agent card
   - Create `Client` with JSON-RPC transport using:
     ```java
     Client.builder(agentCard)
         .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
         .addConsumers(consumers)
         .build()
     ```
   - Configure client with text input/output modes
   - Set up event consumers to capture agent responses
   - **Important:** Client is thread-safe and should be reused for all requests

4. **Tool Definition:**
   - **Name:** `book_agent`
   - **Description:** Dynamically built from the agent card:
     - Primary: Use agent card's `description` field
     - Enhanced: Append skills information from agent card's `skills` list to provide maximum context for LLM tool selection
     - Fallback: If agent card unavailable, use: "Query a specialized book agent about books, authors, genres, and literature"
   - **Input Schema:**
     ```json
     {
       "type": "object",
       "properties": {
         "query": {
           "type": "string",
           "description": "The question or query about books, authors, or literature"
         }
       },
       "required": ["query"]
     }
     ```
   
   **Example implementation:**
   ```java
   private static String buildToolDescription(AgentCard agentCard) {
       if (agentCard == null) {
           return "Query a specialized book agent about books and literature";
       }
       
       StringBuilder description = new StringBuilder(agentCard.description());
       
       // Optionally add skills information
       if (agentCard.skills() != null && !agentCard.skills().isEmpty()) {
           description.append(" Capabilities: ");
           agentCard.skills().forEach(skill -> 
               description.append(skill.name()).append(", ")
           );
       }
       
       return description.toString();
   }
   ```

5. **Execution Flow:**
   - Extract `query` from input parameters
   - Get or initialize the static A2A client instance (lazy initialization on first call)
   - Create user message with the query using `A2A.toUserMessage(query)`
   - Send message via `client.sendMessage(message)` (uses synchronous request/response, not streaming)
   - Collect responses from event consumers using StringBuilder:
     - Handle `MessageEvent`: Accumulate assistant text responses
     - Handle `TaskEvent`: Track task completion status
     - Handle `TaskUpdateEvent`: Monitor progress updates
   - Wait for final response with timeout (60 seconds)
   - Return accumulated result as string

6. **Error Handling:**
   - Handle connection failures to Book Agent (IOException)
   - Handle timeout scenarios (TimeoutException) for queries taking longer than 60 seconds
   - Handle empty or malformed responses
   - **Return error messages as-is** - do not retry automatically
   - Return clear error messages that include:
     - The specific error that occurred
     - Suggestion to verify Book Agent Server is running
     - Suggestion to check network connectivity

7. **Response Processing:**
   - Accumulate all text parts from assistant messages
   - Format response as plain text
   - Include any relevant metadata (task ID, status)

**Verification:**
- Class compiles without errors
- Tool definition matches expected JSON schema
- Client initialization logic is correct (based on A2A SDK examples)

---

### Step 3: Register Book Agent Tool in ToolRegistry

**Files:** [8-agent-to-agent/java/src/main/java/bitovi/activities/tools/ToolRegistry.java](../8-agent-to-agent/java/src/main/java/bitovi/activities/tools/ToolRegistry.java)

Register the new Book Agent tool so it's available to the agent workflow.

**Changes:**
1. In the `static` initialization block, add:
   ```java
   toolExecutors.put("book_agent", BookAgentTool::execute);
   ```

2. In `getAllBedrockTools()`, add:
   ```java
   tools.add(BookAgentTool.getBedrockTool());
   ```

**Verification:**
- Tool registry compiles
- `getAllBedrockTools()` includes the book agent tool
- `executeTool("book_agent", input)` can be called without errors

---

### Step 4: Test Integration with Book Agent

**Files:** 
- [8-agent-to-agent/java/src/main/java/bitovi/AgentToAgentClient.java](../8-agent-to-agent/java/src/main/java/bitovi/AgentToAgentClient.java)

Test the integration end-to-end.

**Test Scenarios:**

1. **Simple Book Query:**
   - Input: "What books by Jane Austen are available?"
   - Expected: Agent uses `book_agent` tool, returns list of Austen books
   - Verification: Check workflow execution logs for tool call and response

2. **Author Information:**
   - Input: "Tell me about books by Charles Dickens"
   - Expected: Agent queries book agent, returns book information
   - Verification: Response contains accurate book data from Gutendex API

3. **Genre Search:**
   - Input: "Find me some science fiction books"
   - Expected: Agent uses book agent tool to search
   - Verification: Results match genre query

4. **Error Scenario:**
   - Stop Book Agent Server (docker compose down)
   - Input: "What books are available?"
   - Expected: Tool returns connection error, agent handles gracefully
   - Verification: Error message is clear and actionable

**Testing Steps:** (manual testing only, no automated tests needed)
1. Ensure Book Agent Server is running (start manually if needed)
2. Verify agent card is accessible: `curl http://localhost:4000/.well-known/agent-card.json`
3. Build Exercise 8: `mvn compile` in `/8-agent-to-agent/java/`
4. Start Exercise 8 worker
5. Run Exercise 8 client with book-related queries
6. Monitor logs for:
   - Tool selection (should choose `book_agent`)
   - A2A client initialization
   - Message exchange with Book Agent
   - Response processing
   - Final answer delivery

**Verification:**
- Agent successfully communicates with Book Agent
- Responses are accurate and relevant
- No connection errors or timeouts (under normal conditions)
- Error handling works when Book Agent is unavailable

---

### Step 5: Optimize and Refine

**Files:** 
- [8-agent-to-agent/java/src/main/java/bitovi/activities/tools/BookAgentTool.java](../8-agent-to-agent/java/src/main/java/bitovi/activities/tools/BookAgentTool.java)

Optimize the implementation based on testing results.

**Optimizations:**

1. **Client Initialization:**
   - Verify static A2A client singleton is working correctly
   - Confirm client is initialized only once across multiple tool calls
   - Check that no client instances are being leaked

2. **Logging:**
   - Add INFO level logs for: client initialization, successful responses, errors
   - Add DEBUG level logs for: request details, response accumulation, event processing
   - Verify: Logs provide useful debugging information without excessive noise

3. **Error Messages:**
   - Refine error messages to be actionable and clear
   - Include specific error context (connection refused, timeout, etc.)
   - Verify: Agent can understand and communicate errors to users

**Verification:**
- Client connections are properly managed (no resource leaks)
- Logging provides adequate troubleshooting information
- Error handling is robust and informative

---

## Success Criteria

The integration is complete when:

1. ✅ A2A client dependencies are added to pom.xml
2. ✅ BookAgentTool class is implemented with static singleton client pattern
3. ✅ BookAgentTool is registered in ToolRegistry
4. ✅ Agent can successfully query the Book Agent via tool calls
5. ✅ Responses from Book Agent are properly processed and returned
6. ✅ Error handling works correctly (returns errors as-is without retry)
7. ✅ Multiple consecutive queries reuse the same client instance

## Questions

*No outstanding questions at this time.*
