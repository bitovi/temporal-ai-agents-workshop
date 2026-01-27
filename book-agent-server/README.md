# Book Agent Server

An A2A (Agent-to-Agent) server that uses AWS Bedrock's Claude model to answer questions about books, authors, genres, and literature.

## Features

- **AI-Powered Responses**: Uses AWS Bedrock Converse API with Claude 3.7 Sonnet
- **Multiple Transport Options**: Supports JSON-RPC, REST (HTTP+JSON), and gRPC
- **A2A Protocol Compliant**: Full Agent-to-Agent protocol support
- **Stateless Architecture**: Each request is independent, no conversation history
- **Error Handling**: Robust error handling with detailed server-side logging

## Prerequisites

- Node.js 20+
- npm or yarn
- AWS credentials with Bedrock access
- Access to Claude 3.7 Sonnet model (`us.anthropic.claude-3-7-sonnet-20250219-v1:0`)

## Setup

### 1. Install Dependencies

```bash
npm install
```

### 2. Configure Environment Variables

Create a `.env` file in the `book-agent-server` directory with the following:

```bash
AWS_ACCESS_KEY_ID=<your-aws-access-key>
AWS_SECRET_ACCESS_KEY=<your-aws-secret-key>
AWS_SESSION_TOKEN=<your-aws-session-token>
AWS_REGION=us-east-1
AWS_MODEL_ID=us.anthropic.claude-3-7-sonnet-20250219-v1:0
```

**Note**: You can copy these values from the workshop config files (e.g., `1-prompt-engineering/config.properties`).

### 3. Start the Server

**Development mode (with auto-reload):**
```bash
npm run dev
```

**Production mode:**
```bash
npm run build
npm run start:prod
```

The server will start on:
- **HTTP/JSON-RPC**: `http://localhost:4000/a2a/jsonrpc`
- **REST**: `http://localhost:4000/a2a/rest`
- **gRPC**: `localhost:4001`
- **Agent Card**: `http://localhost:4000/.well-known/agent-card.json`

## Testing

### Test with HTTP Client

```bash
npm run client:http
```

This will send a test message "Hi there!" to the agent and display the response.

### Test with gRPC Client

```bash
npm run client:grpc
```

### Test Agent Card

```bash
npm run card
```

Or manually:
```bash
curl http://localhost:4000/.well-known/agent-card.json
```

### Custom Test Queries

Edit `http-client.ts` or `grpc-client.ts` to test different questions:

```typescript
const sendParams: MessageSendParams = {
  message: {
    messageId: uuidv4(),
    role: 'user',
    parts: [{ kind: 'text', text: 'Who wrote Moby Dick?' }],
    kind: 'message',
  },
};
```

Example questions to try:
- "Who wrote Moby Dick?"
- "Recommend a science fiction book"
- "What are the main themes in Pride and Prejudice?"
- "Tell me about Ernest Hemingway"

## Architecture

### Components

1. **server.ts**: Main server setup with Express and gRPC endpoints
2. **bedrock-client.ts**: AWS Bedrock integration module
3. **BookAgentExecutor**: Core agent logic that processes messages
4. **http-client.ts**: HTTP/JSON-RPC test client
5. **grpc-client.ts**: gRPC test client

### Message Flow

```
Client → A2A Server (JSON-RPC/REST/gRPC)
  ↓
BookAgentExecutor.execute()
  ↓
Extract user message text
  ↓
Call AWS Bedrock Converse API
  ↓
Return AI-generated response
  ↓
Client receives response
```

### Token Management

- **Maximum tokens**: 12,000 per request
- **Estimation heuristic**: 1 token ≈ 4 characters
- The system automatically logs estimated token counts
- Warning logged if estimated tokens exceed limit

## Known Limitations

- **Stateless**: No conversation history between requests
- **No Streaming**: Responses are returned in full (not streamed)
- **No Caching**: Each request makes a fresh API call
- **No Gutendex Integration**: Currently uses Claude's general knowledge only
- **No Rate Limiting**: Direct passthrough to Bedrock API

## Future Enhancements

- Add Gutendex API integration for real-time book data
- Implement conversation history/memory
- Add streaming response support
- Implement caching for common queries
- Add metrics and telemetry
- Support for book search and recommendations via tools

## Troubleshooting

### Server won't start

- Check that `.env` file exists and contains valid AWS credentials
- Verify ports 4000 and 4001 are available
- Check that all dependencies are installed: `npm install`

### "Empty response from Bedrock API"

- Verify AWS credentials are valid and not expired
- Check that you have access to the Claude model specified in `AWS_MODEL_ID`
- Review server logs for detailed error messages

### Client connection errors

- Ensure the server is running: `npm run dev`
- Verify the correct port (4000 for HTTP, 4001 for gRPC)
- Check firewall settings

## Logging

All logs are output to the console with prefixes:
- `[BookAgent]`: Agent-level operations
- `[Bedrock]`: AWS Bedrock API calls and responses

Error messages are logged server-side with full details, while clients receive generic error messages for security.

## License

Part of the Bitovi AI Agents Workshop.
