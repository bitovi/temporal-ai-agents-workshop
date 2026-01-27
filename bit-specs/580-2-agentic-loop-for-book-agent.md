# Agentic Loop for Book Agent

## Overview
Implement a ReAct (Reasoning and Acting) agentic loop in the book agent server to enable multi-step book queries using the Gutendex API. The agent will use AWS Bedrock's tool calling capabilities to reason about user requests and execute multiple API calls as needed.

## Current State
- **File**: [book-agent-server/server.ts](../book-agent-server/server.ts)
- **Status**: Basic Bedrock integration complete
  - AWS Bedrock Converse API implemented in [bedrock-client.ts](../book-agent-server/bedrock-client.ts)
  - Single-turn stateless conversations working
  - No tool calling or agentic loop yet
- **Dependencies**: `@aws-sdk/client-bedrock-runtime`, `@a2a-js/sdk`, `express`
- **API Documentation**: [gutendex-docs.txt](../book-agent-server/gutendex-docs.txt) describes the Gutendex book API

## Goal
Enable the book agent to:
1. Accept tool definitions and pass them to Bedrock
2. Receive tool use requests from the LLM
3. Execute HTTP calls to the Gutendex API
4. Maintain conversation context during the ReAct loop
5. Iterate through multiple reasoning/action cycles until reaching a final answer
6. Return the complete answer to the user

## Prerequisites
- Node.js 18+ (required for native `fetch` API support)
- AWS Bedrock access with appropriate credentials

---

## Implementation Plan

### Step 1: Define Gutendex API Tool Specifications

**What**: Create TypeScript interfaces and tool definitions for Gutendex API endpoints  
**Files**: Create [book-agent-server/gutendex-tools.ts](../book-agent-server/gutendex-tools.ts)

Create tool specifications following the Bedrock ToolSpecification format:

1. **search_books** tool:
   - Description: "Search for books in the Project Gutenberg catalog by title, author, topic, language, or other filters"
   - Parameters:
     - `search` (string, optional): Search terms for author names and book titles
     - `topic` (string, optional): Key-phrase in bookshelves or subjects (e.g., "children", "science fiction")
     - `languages` (string, optional): Comma-separated language codes (e.g., "en", "fr,es")
     - `author_year_start` (number, optional): Authors alive after this year
     - `author_year_end` (number, optional): Authors alive before this year
     - `sort` (string, optional): Sort order - "ascending", "descending", or "popular"

2. **get_book_by_id** tool:
   - Description: "Get detailed information about a specific book by its Project Gutenberg ID"
   - Parameters:
     - `id` (number, required): The Project Gutenberg book ID

Interface structure:
```typescript
import { Tool, ToolInputSchema } from '@aws-sdk/client-bedrock-runtime';

// Truncation constants for token optimization
const MAX_SEARCH_RESULTS = 10;
const MAX_TEXT_FIELD_LENGTH = 500;
const MAX_ARRAY_ITEMS = 3;

export interface GutendexSearchParams {
  search?: string;
  topic?: string;
  languages?: string;
  author_year_start?: number;
  author_year_end?: number;
  sort?: string;
}

export interface GutendexBookIdParams {
  id: number;
}

export function getGutendexTools(): Tool[]
```

**Verification**:
- Import the module in [server.ts](../book-agent-server/server.ts) successfully
- Log the tool definitions to console and verify structure matches Bedrock format
- Check that all required fields (name, description, inputSchema) are present

---

### Step 2: Implement Gutendex HTTP Client Functions

**What**: Create functions to call the Gutendex API based on tool parameters  
**Files**: Update [book-agent-server/gutendex-tools.ts](../book-agent-server/gutendex-tools.ts)

Implement execution functions:

```typescript
export async function executeSearchBooks(params: GutendexSearchParams): Promise<string>
export async function executeGetBookById(params: GutendexBookIdParams): Promise<string>
```

Implementation details:
- Base URL: `https://gutendex.com`
- Use Node.js native `fetch` API for HTTP requests (requires Node 18+)
- Build query strings from parameters (e.g., `/books?search=dickens&languages=en`)
- Handle pagination - return first page of results only
- Parse JSON responses and return as JSON strings to LLM
- Include error handling for network failures and invalid IDs

**Truncation Strategy (Token Optimization):**
- Limit search results to `MAX_SEARCH_RESULTS` (10) books maximum
- Truncate any text field >`MAX_TEXT_FIELD_LENGTH` (500) characters (subjects, descriptions, summaries)
- Include only essential book fields: id, title, authors (name only), subjects (first `MAX_ARRAY_ITEMS`), languages, download_count
- For arrays (subjects, bookshelves): limit to first `MAX_ARRAY_ITEMS` (3) items
- Strip unnecessary formatting and whitespace

These constants are defined at the top of [gutendex-tools.ts](../book-agent-server/gutendex-tools.ts) and can be tuned if token limits are still exceeded in practice.

Response formatting:
- For search results: Return JSON string with:
  - `count`: Total number of matching books
  - `results`: Array of up to 10 books with truncated fields
  - `note`: "Showing first 10 of {count} results" (when count > 10)
- For individual books: Return JSON string with full book object (fields truncated to 500 chars)
- For errors: Return JSON object as string: `{"error": "Could not fetch book with ID 123"}`

**Verification**:
- Test search with various parameters: `executeSearchBooks({ search: 'dickens' })`
- Test get by ID: `executeGetBookById({ id: 11 })` (should return Alice's Adventures in Wonderland)
- Verify responses are concise and LLM-friendly
- Test error cases: invalid ID, network timeout

---

### Step 3: Update Bedrock Client to Support Tool Calling

**What**: Extend the Bedrock client to handle tool configurations and tool use responses  
**Files**: [book-agent-server/bedrock-client.ts](../book-agent-server/bedrock-client.ts)

**Update interfaces to support Bedrock's native tool calling format:**

```typescript
import { 
  Tool,
  Message,
  ContentBlock,
  ToolInputSchema
} from '@aws-sdk/client-bedrock-runtime';

export interface ToolUse {
  toolUseId: string;
  name: string;
  input: Record<string, any>;
}

export interface BedrockResponse {
  text?: string;
  toolUses?: ToolUse[];
  stopReason?: string; // 'end_turn', 'tool_use', 'max_tokens', etc.
}

// Accept Bedrock's native Message[] format
export async function callBedrockWithTools(
  messages: Message[],
  systemPrompt: string,
  tools: Tool[]
): Promise<BedrockResponse>
```

**Note**: `toolUse` and `toolResult` are properties of `ContentBlock`, not separate types. Access them via:
```typescript
if (block.toolUse) { /* handle tool use */ }
if (block.toolResult) { /* handle tool result */ }
```

**Implementation details:**

Update `ConverseCommand` to include:
```typescript
{
  modelId: process.env.AWS_MODEL_ID,
  system: [{ text: systemPrompt }],
  messages: messages, // Native Bedrock Message[] with ContentBlock arrays
  toolConfig: {
    tools: tools
  }
}
```

Parse response to detect:
- Text content blocks: `block.text`
- Tool use blocks: `block.toolUse` containing `toolUseId`, `name`, `input`
- Stop reason: `response.stopReason` to determine if tool use is requested

Return `BedrockResponse` with:
- `text`: Present when LLM provides a final answer
- `toolUses`: Array of tool requests when LLM wants to use tools
- `stopReason`: Bedrock's stop reason for debugging

**Verification**:
- Update system prompt to mention available tools
- Test with a query requiring a tool: "Find books by Charles Dickens"
- Verify response contains `toolUses` array instead of just text
- Log tool use details and confirm they match expected format
- Check that `toolUseId` is preserved for response tracking

---

### Step 4: Implement Conversation Context Management

**What**: Create a context manager to track conversation history using Bedrock's native Message format  
**Files**: Create [book-agent-server/conversation-context.ts](../book-agent-server/conversation-context.ts)

Implement a `ConversationContext` class using Bedrock's native types:

```typescript
import { Message, ContentBlock } from '@aws-sdk/client-bedrock-runtime';

export class ConversationContext {
  private messages: Message[] = [];
  
  addUserMessage(text: string): void
  addAssistantMessage(content: ContentBlock[]): void
  addToolResult(toolUseId: string, content: string): void
  getMessages(): Message[]
  clear(): void
  estimateTokenCount(): number
  truncateOldest(): void
}
```

**Key behaviors:**
- Maintain ordered list of Bedrock `Message` objects alternating between user and assistant
- Messages use `ContentBlock[]` arrays to support text, tool use, and tool result blocks
- Follow Bedrock's tool calling pattern:
  1. User message with text
  2. Assistant message with toolUse blocks (when requesting tools)
  3. User message with toolResult blocks (after tool execution)
  4. Loop back to assistant message (with final answer or more tool requests)

**Message structure examples:**
```typescript
// User text message
{
  role: 'user',
  content: [{ text: 'Find books by Charles Dickens' }]
}

// Assistant message with tool use
{
  role: 'assistant',
  content: [
    { toolUse: { toolUseId: 'xyz', name: 'search_books', input: { search: 'dickens' } } }
  ]
}

// User message with tool result
{
  role: 'user',
  content: [
    { toolResult: { toolUseId: 'xyz', content: '{"results": [...]}' } }
  ]
}
```

**Token Management:**
- `estimateTokenCount()`: Calculate approximate tokens (4 chars ≈ 1 token) for all messages
- `truncateOldest()`: Remove oldest user/assistant message pair to stay under token limit
- Keep initial user question and never truncate it

**Token Estimation Implementation:**
```typescript
estimateTokenCount(): number {
  let totalChars = 0;
  for (const message of this.messages) {
    for (const block of message.content || []) {
      if (block.text) {
        totalChars += block.text.length;
      } else if (block.toolUse) {
        totalChars += JSON.stringify(block.toolUse.input).length;
      } else if (block.toolResult) {
        // toolResult.content can be an array of ContentBlocks or a simple string
        // For simplicity, stringify the entire content
        totalChars += JSON.stringify(block.toolResult.content).length;
      }
    }
  }
  return Math.ceil(totalChars / 4);
}
```

**Verification**:
- Create context and add messages following Bedrock's tool calling pattern
- Verify message structure matches Bedrock's expected format
- Test token estimation and truncation logic
- Confirm alternating user/assistant roles are maintained

---

### Step 5: Implement Tool Execution Router

**What**: Create a router to dispatch tool calls to appropriate execution functions  
**Files**: Create [book-agent-server/tool-executor.ts](../book-agent-server/tool-executor.ts)

**File imports:**
```typescript
import { executeSearchBooks, executeGetBookById, GutendexSearchParams, GutendexBookIdParams } from './gutendex-tools';
```

Implement execution logic:

```typescript
export async function executeTool(
  toolName: string, 
  toolInput: Record<string, any>
): Promise<string>
```

Router logic:
```typescript
switch (toolName) {
  case 'search_books':
    return await executeSearchBooks(toolInput as GutendexSearchParams);
  case 'get_book_by_id':
    return await executeGetBookById(toolInput as GutendexBookIdParams);
  default:
    throw new Error(`Unknown tool: ${toolName}`);
}
```

Error handling:
- Validate tool inputs before execution
- Catch and format HTTP errors
- Return error messages as tool results (don't throw)
- Log all tool executions for debugging

**Verification**:
- Test routing for each tool type
- Verify tool input validation works
- Test error handling with invalid parameters
- Confirm tool results are returned as strings

---

### Step 6: Implement ReAct Loop in BookAgentExecutor

**What**: Replace single-turn logic with a multi-step ReAct loop using Bedrock's native tool calling pattern  
**Files**: [book-agent-server/server.ts](../book-agent-server/server.ts)

**File imports:**
```typescript
import { ConversationContext } from './conversation-context';
import { callBedrockWithTools } from './bedrock-client';
import { getGutendexTools } from './gutendex-tools';
import { executeTool } from './tool-executor';
```

Update `BookAgentExecutor.execute()` method to implement Bedrock's native ReAct cycle:

**ReAct Loop using Bedrock's Tool Calling Pattern:**

1. **User message**: Initial question added to context
2. **Assistant response**: LLM decides to use tools or provide final answer
3. **Tool execution**: Execute requested tools and add results as toolResult blocks
4. **Loop**: Bedrock processes tool results and continues reasoning
5. **Final answer**: LLM provides text response when complete

**Implementation:**

```typescript
const context = new ConversationContext();
context.addUserMessage(userText);

const tools = getGutendexTools();
const maxIterations = 10;
let finalAnswer: string | undefined;

for (let i = 0; i < maxIterations; i++) {
  console.log(`[ReAct] Iteration ${i + 1}/${maxIterations}`);
  
  // Check token limit before making call
  const tokenCount = context.estimateTokenCount();
  console.log(`[ReAct] Estimated tokens: ${tokenCount}`);
  
  if (tokenCount > 12000) {
    console.warn(`[ReAct] Token limit exceeded (${tokenCount}), truncating oldest context`);
    context.truncateOldest();
  }
  
  // Call Bedrock with current context and tools
  const response = await callBedrockWithTools(
    context.getMessages(),
    SYSTEM_PROMPT,
    tools
  );
  
  // Check for final answer (text response without tool use)
  if (response.text && (!response.toolUses || response.toolUses.length === 0)) {
    console.log(`[ReAct] Final answer ready, exiting loop`);
    finalAnswer = response.text;
    break;
  }
  
  // Handle tool use requests
  if (response.toolUses && response.toolUses.length > 0) {
    console.log(`[ReAct] Processing ${response.toolUses.length} tool request(s)`);
    
    // Add assistant's tool use request to context
    const toolUseBlocks = response.toolUses.map(tu => ({
      toolUse: {
        toolUseId: tu.toolUseId,
        name: tu.name,
        input: tu.input
      }
    }));
    context.addAssistantMessage(toolUseBlocks);
    
    // Execute all requested tools
    for (const toolUse of response.toolUses) {
      console.log(`[ReAct] Executing tool: ${toolUse.name}`);
      console.log(`[ReAct] Tool input:`, JSON.stringify(toolUse.input, null, 2));
      
      try {
        const result = await executeTool(toolUse.name, toolUse.input);
        console.log(`[ReAct] Tool result length: ${result.length} chars`);
        
        // Add tool result to context using Bedrock's native format
        context.addToolResult(toolUse.toolUseId, result);
      } catch (error) {
        console.error(`[ReAct] Tool execution failed:`, error);
        const errorResult = JSON.stringify({ 
          error: `Tool execution failed: ${error instanceof Error ? error.message : 'Unknown error'}` 
        });
        context.addToolResult(toolUse.toolUseId, errorResult);
      }
    }
    
    // Continue loop - Bedrock will process tool results in next iteration
    continue;
  }
  
  // Empty response handling
  if (!response.text && (!response.toolUses || response.toolUses.length === 0)) {
    console.warn(`[ReAct] Empty response from Bedrock on iteration ${i + 1}`);
    if (i === 0) {
      // First iteration - retry once
      console.log(`[ReAct] Retrying...`);
      continue;
    } else {
      // Subsequent iteration - treat as error
      finalAnswer = "I apologize, but I encountered an issue processing your request. Please try again.";
      break;
    }
  }
}

// Handle max iterations reached
if (!finalAnswer) {
  console.warn(`[ReAct] Max iterations (${maxIterations}) reached without final answer`);
  finalAnswer = "I've gathered information about your query, but need more time to provide a complete answer. Based on what I found so far, " +
    "I can tell you that the Gutendex catalog contains extensive book data. Please try rephrasing your question or making it more specific.";
}

// Publish final answer
const responseMessage: Message = {
  kind: 'message',
  messageId: uuidv4(),
  role: 'agent',
  parts: [{ kind: 'text', text: finalAnswer }],
  contextId: requestContext.contextId,
};

eventBus.publish(responseMessage);
eventBus.finished();
```

**System Prompt (Dynamically Generated):**
```typescript
// Generate tool descriptions dynamically from tool definitions
function generateSystemPrompt(tools: Tool[]): string {
  const toolDescriptions = tools
    .map(tool => `- ${tool.toolSpec?.name}: ${tool.toolSpec?.description}`)
    .join('\n');
  
  return `You are a knowledgeable book assistant with access to the Gutendex API (Project Gutenberg catalog).

When a user asks about books, authors, or literature:
1. Analyze what information you need to answer their question
2. Use the available tools to search for books or get book details
3. Process the tool results to extract relevant information
4. Provide a helpful, accurate answer citing specific books by title and author when possible

You have access to these tools:
${toolDescriptions}

Be thorough but concise. If search results are truncated, mention the total count available.
When tool results indicate errors or no matches, explain this clearly to the user and suggest alternatives.`;
}

const tools = getGutendexTools();
const SYSTEM_PROMPT = generateSystemPrompt(tools);
```

**Verification**:
- Test simple query requiring one tool call: "Find books by Jane Austen"
- Test complex query requiring multiple calls: "Find science fiction books written after 1950"
- Test query requiring reasoning: "What books by Charles Dickens are available in Spanish?"
- Verify loop stops after final answer (doesn't continue unnecessarily)
- Confirm max iteration limit prevents infinite loops
- Verify tool results are properly added to context using toolResult blocks
- Check token counting and truncation work correctly

---

### Step 7: Add Logging and Observability

**What**: Comprehensive logging is already implemented in Step 6's ReAct loop  
**Files**: [book-agent-server/server.ts](../book-agent-server/server.ts)

The ReAct loop includes logging at all key points:
- Start of each iteration with iteration count
- Token count estimation before each Bedrock call
- Token limit warnings and truncation events
- Tool execution requests (name and input parameters)
- Tool execution results (success/error and result length)
- Final answer detection
- Loop exit reasons (answer reached, max iterations, error)

**Log format examples from Step 6:**
```typescript
console.log(`[ReAct] Iteration ${i + 1}/${maxIterations}`);
console.log(`[ReAct] Estimated tokens: ${tokenCount}`);
console.warn(`[ReAct] Token limit exceeded (${tokenCount}), truncating oldest context`);
console.log(`[ReAct] Processing ${response.toolUses.length} tool request(s)`);
console.log(`[ReAct] Executing tool: ${toolUse.name}`);
console.log(`[ReAct] Tool input:`, JSON.stringify(toolUse.input, null, 2));
console.log(`[ReAct] Tool result length: ${result.length} chars`);
console.log(`[ReAct] Final answer ready, exiting loop`);
console.warn(`[ReAct] Max iterations (${maxIterations}) reached without final answer`);
```

**Note**: Metrics are internal to server logs only - tool call counts and token usage are not exposed to clients.

**Verification**:
- Run a multi-step query and review logs
- Verify each ReAct step is clearly visible
- Confirm tool parameters and results are logged
- Check that logs help diagnose issues

---

### Step 8: Handle Edge Cases and Errors

**What**: Add robust error handling throughout the ReAct loop  
**Files**: [book-agent-server/server.ts](../book-agent-server/server.ts)

Handle edge cases:

1. **No tool calls and no text**: Empty Bedrock response
   - Log warning
   - Retry once
   - If still empty, return error message

2. **Max iterations reached**: Loop limit without final answer
   - Log warning  
   - Return best possible answer based on information gathered during iterations
   - Acknowledge incomplete analysis: "I've gathered information about your query, but need more time for a complete answer..."
   - Include specific details from tool results when available

3. **Tool execution failures**: API errors, timeouts
   - Don't fail the whole request
   - Return error as tool result to LLM
   - Let LLM decide how to proceed or provide answer with caveats

4. **Invalid tool parameters**: LLM provides malformed input
   - Validate parameters before execution
   - Return validation error as tool result
   - LLM can retry with corrected parameters

5. **Bedrock API errors**: Throttling, credential issues
   - Catch and log error
   - Return graceful error message to user
   - Don't expose internal error details

**Verification**:
- Test with Gutendex API temporarily unavailable (simulate timeout)
- Test with intentionally malformed tool parameters
- Test with very complex query requiring >5 iterations
- Verify graceful degradation in all cases
- Confirm user always receives some response (never hangs)

---

### Step 9: End-to-End Testing with Varied Queries

**What**: Comprehensive testing with realistic book-related queries  
**Files**: [book-agent-server/http-client.ts](../book-agent-server/http-client.ts)

Test scenarios:

1. **Simple search**: "Find books by Ernest Hemingway"
   - Should: Use search_books with author name
   - Verify: Returns list of books with titles

2. **Filtered search**: "Find French books written in the 19th century"
   - Should: Use search_books with language=fr and year filters
   - Verify: Returns appropriate filtered results

3. **Specific book lookup**: "Tell me about Alice's Adventures in Wonderland"
   - Should: Use search_books to find book, then get_book_by_id for details
   - Verify: Provides detailed information about the specific book

4. **Complex query**: "What are the most popular science fiction books available?"
   - Should: Use search_books with topic=science fiction and sort=popular
   - Verify: Returns list sorted by popularity

5. **Conversational query**: "I'm looking for a good book to read. I like mysteries."
   - Should: Search for mystery books and provide recommendations
   - Verify: Provides thoughtful suggestions

6. **Ambiguous query**: "Books about love"
   - Should: Use search_books with appropriate terms
   - Verify: Returns relevant results and explains choices

7. **Error: Invalid book ID**: "Tell me about book ID 999999999"
   - Should: Call get_book_by_id with invalid ID
   - Verify: Returns graceful error message explaining book not found

8. **Error: Network simulation**: Temporarily block gutendex.com to simulate API failure
   - Should: Tool execution returns error
   - Verify: Agent acknowledges issue and suggests trying again

9. **Token limit test**: Complex query requiring many tool calls
   - Should: Execute multiple iterations with tool calls
   - Verify: Token count warnings logged, context truncation occurs if needed

**Verification for each test**:
- Run query through HTTP client
- Check server logs for ReAct loop steps
- Verify tool calls are appropriate for the query
- Confirm final answer is helpful and accurate
- Check response time is reasonable (<10 seconds)
- Test across all transport types (JSON-RPC, REST, gRPC)

---

## Configuration Decisions

Based on requirements analysis:

1. **Tool Execution**: Sequential execution (one tool at a time)
   - Simpler implementation and debugging
   - Adequate performance for book queries

2. **Max Iterations**: 10 iterations per ReAct loop
   - Provides sufficient depth for complex multi-step queries
   - Prevents infinite loops

3. **Caching**: No caching of Gutendex API responses
   - Keeps implementation simple
   - API responses are fast enough

4. **Metrics**: Internal logging only
   - Detailed logs for debugging on server side
   - No exposure of tool counts or token usage to clients

5. **Context Persistence**: Fresh context per user message
   - Each message starts a new ReAct loop
   - Stateless design aligns with current A2A server architecture

6. **Token Management**: Automatic truncation when exceeding 12,000 tokens
   - Log warning when approaching limit
   - Truncate oldest message pairs (excluding initial user question)
   - Prevents request failures due to token limits

7. **Message Format**: Use Bedrock's native tool calling pattern
   - Assistant messages with toolUse blocks for tool requests
   - User messages with toolResult blocks for tool responses
   - Maintains proper message alternation (user/assistant)

## Questions

No additional questions at this time. All review findings have been incorporated into the spec.

**Summary of changes made:**
1. ✅ Fixed imports in Steps 1 and 3 to match TypeScript AWS SDK structure
2. ✅ Added configurable constants for truncation limits in Step 1
3. ✅ Added detailed `estimateTokenCount()` implementation in Step 4
4. ✅ Made system prompt dynamically generated from tool definitions in Step 6
5. ✅ Clarified that `toolUse` and `toolResult` are ContentBlock properties in Step 3
6. ✅ Confirmed error response format consistency in Step 2
