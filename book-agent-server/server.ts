// server.ts
import dotenv from 'dotenv';
import express from 'express';
import { Server, ServerCredentials } from '@grpc/grpc-js';
import { v4 as uuidv4 } from 'uuid';
import { AgentCard, Message, AGENT_CARD_PATH, TextPart } from '@a2a-js/sdk';

dotenv.config();
import {
  AgentExecutor,
  RequestContext,
  ExecutionEventBus,
  DefaultRequestHandler,
  InMemoryTaskStore,
} from '@a2a-js/sdk/server';
import { agentCardHandler, jsonRpcHandler, restHandler, UserBuilder } from '@a2a-js/sdk/server/express';
import { grpcService, A2AService } from '@a2a-js/sdk/server/grpc';
import { callBedrock } from './bedrock-client';

// 1. Define your agent's identity card.
const bookAgentCard: AgentCard = {
  name: 'Book Agent',
  description: 'An agent that can answer questions about books.',
  protocolVersion: '0.3.0',
  version: '0.1.0',
  url: 'http://localhost:4000/a2a/jsonrpc', // The public URL of your agent server
  skills: [{ id: 'chat', name: 'Chat', description: 'Chat about books', tags: ['chat'] }],
  capabilities: {
    pushNotifications: false,
  },
  defaultInputModes: ['text'],
  defaultOutputModes: ['text'],
  additionalInterfaces: [
    { url: 'http://localhost:4000/a2a/jsonrpc', transport: 'JSONRPC' }, // Default JSON-RPC transport
    { url: 'http://localhost:4000/a2a/rest', transport: 'HTTP+JSON' }, // HTTP+JSON/REST transport
    { url: 'localhost:4001', transport: 'GRPC' }, // GRPC transport
  ],
};

// 2. Implement the agent's logic.
class BookAgentExecutor implements AgentExecutor {
  private readonly systemPrompt = 'You are a knowledgeable book assistant. You can answer questions about books, authors, genres, and literature. You have access to the Gutendex API for book information but will start with general knowledge.';

  async execute(requestContext: RequestContext, eventBus: ExecutionEventBus): Promise<void> {
    try {
      console.log(`[BookAgent] Received message with context ID: ${requestContext.contextId}`);
      
      // Extract user message text from requestContext.userMessage.parts
      const userText = requestContext.userMessage.parts
        .filter((p): p is TextPart => p.kind === 'text')
        .map((p) => p.text)
        .join(' ');

      console.log(`[BookAgent] User message: ${userText}`);

      // Call Bedrock with single-turn conversation (stateless)
      const aiResponse = await callBedrock(
        [{ role: 'user', content: userText }],
        this.systemPrompt
      );

      console.log(`[BookAgent] AI response generated successfully`);

      // Create response message with AI-generated text
      const responseMessage: Message = {
        kind: 'message',
        messageId: uuidv4(),
        role: 'agent',
        parts: [{ kind: 'text', text: aiResponse }],
        contextId: requestContext.contextId,
      };

      // Publish the message and signal that the interaction is finished
      eventBus.publish(responseMessage);
      eventBus.finished();
    } catch (error) {
      console.error('[BookAgent] Error processing request:', error);
      
      // Return generic error message to client
      const errorMessage: Message = {
        kind: 'message',
        messageId: uuidv4(),
        role: 'agent',
        parts: [{ kind: 'text', text: 'I apologize, but I encountered an error processing your request. Please try again later.' }],
        contextId: requestContext.contextId,
      };
      
      eventBus.publish(errorMessage);
      eventBus.finished();
    }
  }

  // cancelTask is not needed for this simple, non-stateful agent.
  cancelTask = async (): Promise<void> => {};
}

// 3. Set up and run the server.
const agentExecutor = new BookAgentExecutor();
const requestHandler = new DefaultRequestHandler(
  bookAgentCard,
  new InMemoryTaskStore(),
  agentExecutor
);

const app = express();

app.use(`/${AGENT_CARD_PATH}`, agentCardHandler({ agentCardProvider: requestHandler }));
app.use('/a2a/jsonrpc', jsonRpcHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }));
app.use('/a2a/rest', restHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }));

app.listen(4000, () => {
  console.log(`🚀 HTTP Server started on http://localhost:4000`);
});

const server = new Server();
server.addService(A2AService, grpcService({
  requestHandler,
  userBuilder: UserBuilder.noAuthentication,
}));
server.bindAsync(`localhost:4001`, ServerCredentials.createInsecure(), () => {
  console.log(`🚀 gRPC Server started on localhost:4001`);
});