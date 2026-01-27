# Book Agent with AWS Bedrock Converse API

## Overview
Implement a simple agentic loop using the AWS Bedrock Converse API within the existing A2A (Agent-to-Agent) server architecture. The agent will handle synchronous request/response interactions, allowing clients to ask questions about books and receive AI-generated responses powered by AWS Bedrock.

## Current State
- **File**: `book-agent-server/server.ts`
- **Status**: Basic A2A server scaffold exists with:
  - Agent card definition (name, description, skills, capabilities)
  - Express server with JSON-RPC, REST, and gRPC endpoints
  - `BookAgentExecutor` class that returns "Hello, world!" stub response
  - HTTP and gRPC test clients available
- **Dependencies**: `@a2a-js/sdk`, `express`, `@grpc/grpc-js`, `uuid`
- **Missing**: AWS Bedrock integration, agentic loop logic, environment configuration

## Goal
Enable the book agent to:
1. Receive user messages through A2A protocol
2. Process messages through AWS Bedrock Converse API
3. Return AI-generated responses synchronously
4. Maintain stateless operation (no conversation history initially)

---

## Implementation Plan

### Step 1: Add AWS Bedrock Dependencies
**What**: Install AWS SDK for Bedrock Runtime
**Files**: `book-agent-server/package.json`

Add the following dependency:
```json
"@aws-sdk/client-bedrock-runtime": "^3.x.x"
```

**Verification**:
- Run `npm install` in `book-agent-server/` directory
- Verify `node_modules/@aws-sdk/client-bedrock-runtime` exists
- Check that TypeScript can resolve the import: `import { BedrockRuntimeClient, ConverseCommand } from '@aws-sdk/client-bedrock-runtime'`

---

### Step 2: Create Environment Configuration
**What**: Set up environment variables for AWS credentials and model configuration
**Files**: Create `book-agent-server/.env`

Add configuration following the pattern from workshop config files (e.g., `1-prompt-engineering/config.properties`):
```
AWS_ACCESS_KEY_ID=<from workshop config>
AWS_SECRET_ACCESS_KEY=<from workshop config>
AWS_SESSION_TOKEN=<from workshop config>
AWS_REGION=us-east-1
AWS_MODEL_ID=us.anthropic.claude-3-7-sonnet-20250219-v1:0
```

Also install `dotenv`:
```json
"dotenv": "^16.0.0"
```

**Verification**:
- Run `npm install dotenv`
- Add `import dotenv from 'dotenv'; dotenv.config();` at the top of `server.ts`
- Console log `process.env.AWS_REGION` to verify environment loads correctly
- Check that all required environment variables are accessible

---

### Step 3: Create Bedrock Client Helper
**What**: Create a utility module for AWS Bedrock interactions
**Files**: Create `book-agent-server/bedrock-client.ts`

Implement a helper following the pattern from `0-environment-setup/typescript/src/activities.ts`:
- Initialize `BedrockRuntimeClient` with credentials from environment
- Create a function to send messages via `ConverseCommand`
- Handle message history as an array of `{ role: 'user' | 'assistant', content: string }`
- Return the assistant's response text

Key functionality:
```typescript
export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export async function callBedrock(
  messages: ChatMessage[],
  systemPrompt?: string
): Promise<string>
```

**Verification**:
- Create a test file that imports and calls `callBedrock` with a simple message
- Run: `ts-node bedrock-client-test.ts`
- Verify response contains text from Claude model
- Check console logs show successful API call

---

### Step 4: Implement Agentic Loop in BookAgentExecutor
**What**: Update `BookAgentExecutor.execute()` to call Bedrock and return AI response
**Files**: `book-agent-server/server.ts`

Modify the `BookAgentExecutor` class:
1. Extract user message text from `requestContext.userMessage.parts`
2. Create single-turn conversation (stateless design - no history tracking)
3. Call Bedrock client with system prompt about books
4. Create response message with AI-generated text
5. Publish response via `eventBus.publish()`
6. Signal completion via `eventBus.finished()`

Example extraction code:
```typescript
import { TextPart } from '@a2a-js/sdk';

const userText = requestContext.userMessage.parts
  .filter((p): p is TextPart => p.kind === 'text')
  .map((p) => p.text)
  .join(' ');
```

System prompt:
```
"You are a knowledgeable book assistant. You can answer questions about books, authors, genres, and literature. You have access to the Gutendex API for book information but will start with general knowledge."
```

**Verification**:
- Start the server: `npm run dev` in `book-agent-server/`
- Use HTTP client: `npm run client:http`
- Verify agent responds with AI-generated content instead of "Hello, world!"
- Check server console shows Bedrock API calls
- Test with various questions: "Who wrote Moby Dick?", "Recommend a science fiction book"

---

### Step 5: Add Error Handling and Logging
**What**: Implement robust error handling for API failures
**Files**: `book-agent-server/server.ts`, `book-agent-server/bedrock-client.ts`

Add error handling for:
- AWS credential issues
- Bedrock API errors (rate limits, throttling)
- Empty or malformed responses
- Network timeouts

Implement logging (using console.log):
- Log each user message received
- Log Bedrock API request/response
- Log any errors with context

Note: Keep error messages on server side only - return generic error responses to client

**Verification**:
- Test with invalid AWS credentials (temporarily modify `.env`)
- Verify error messages are clear and logged
- Verify server doesn't crash on errors
- Test error response reaches client properly
- Restore valid credentials and verify normal operation resumes

---

### Step 6: Token Limit Considerations
**What**: Ensure requests stay within token limits
**Files**: `book-agent-server/bedrock-client.ts`

Implement token awareness:
- Document maximum token limit: 12,000 tokens
- Use heuristic: 1 token ≈ 4 characters for estimation
- Add comments about token counting (though not implementing full tracking initially)
- Ensure single-turn requests stay well under limit
- Keep system prompt concise

**Verification**:
- Test with long user messages (several paragraphs)
- Verify responses complete successfully
- Check that Bedrock doesn't return token limit errors
- Document typical token usage in logs

---

### Step 7: Test with Different Transports
**What**: Verify agent works across JSON-RPC, REST, and gRPC
**Files**: `book-agent-server/http-client.ts`, `book-agent-server/grpc-client.ts`

Test scenarios:
- HTTP client with JSON-RPC endpoint
- HTTP client with REST endpoint
- gRPC client
- Agent card accessibility: `curl http://localhost:4000/.well-known/agent-card.json`

**Verification**:
- Run `npm run client:http` - verify response
- Run `npm run client:grpc` - verify response
- Test direct REST calls with curl or Postman
- Verify all transports return same quality responses
- Check that response format matches A2A protocol spec

---

### Step 8: Documentation and Final Testing
**What**: Document the implementation and perform final end-to-end testing
**Files**: `book-agent-server/README.md` (update)

Document:
- Environment setup requirements
- How to start the server
- How to test with provided clients
- Known limitations (stateless, no tool calling yet)
- Future enhancement opportunities (Gutendex integration)

Final testing:
- Verify all transports work (JSON-RPC, REST, gRPC)
- Test various book-related queries
- Confirm error handling works
- Validate agent card is correct

**Verification**:
- README.md is clear and complete
- New developers can follow documentation to run agent
- All success criteria are met
- Server is stable under normal use

---

## Testing Strategy

### Manual Testing
Use the provided test clients:
```bash
# Terminal 1: Start server
cd book-agent-server
npm run start

# Terminal 2: Test with HTTP client
npm run client:http

# Terminal 3: Test with gRPC client
npm run client:grpc

# Terminal 4: Verify agent card
npm run card
```

---

## Success Criteria
- ✅ Client can send text messages to book agent
- ✅ Agent responds with AI-generated content from Bedrock
- ✅ Responses are contextually relevant to books/literature
- ✅ All three transports (JSON-RPC, REST, gRPC) work correctly
- ✅ Error handling prevents crashes
- ✅ Response time is reasonable (< 5 seconds typical)
- ✅ Agent card is accessible and correctly describes capabilities

---

## Technical Notes

### AWS Bedrock Converse API Pattern
Based on workshop examples, the Converse API usage pattern:
```typescript
const response = await client.send(
  new ConverseCommand({
    modelId: process.env.AWS_MODEL_ID,
    system: [{ text: systemPrompt }],
    messages: [
      {
        role: 'user',
        content: [{ text: userMessage }]
      }
    ]
  })
)
```

### Message Flow
1. Client → A2A Server (via JSON-RPC/REST/gRPC)
2. A2A Server → `BookAgentExecutor.execute()`
3. Executor → Bedrock Client → AWS Bedrock API
4. AWS Bedrock → Response text
5. Executor → `eventBus.publish()` → Client response

### Performance Considerations
- Bedrock API typically responds in 1-3 seconds
- Synchronous (non-streaming) request/response
- No caching
- No rate limiting on server side
- Client waits for full response before receiving any data
- Maximum token limit: 12,000 tokens
- Stateless design - no memory overhead for conversation tracking

---

## Design Decisions

**Architecture**: Stateless, synchronous request/response pattern
- No conversation history tracking between requests
- Each request is independent
- Simple and reliable for workshop context

**Error Handling**: Server-side logging only
- Use console.log for all logging
- Return generic error messages to clients
- Log detailed error information server-side for debugging

**Performance**: No rate limiting or caching
- Direct passthrough to Bedrock API
- Token limit: 12,000 tokens maximum
- Synchronous responses (no streaming)

**Scope**: Minimal viable implementation
- No metrics/telemetry
- No Gutendex integration initially
- Focus on stable Bedrock integration
- Foundation for future enhancements

---

## Questions

No outstanding questions at this time. All review feedback has been incorporated into the spec.
