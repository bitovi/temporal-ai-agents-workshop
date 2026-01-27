// server.ts
import express from 'express';
import { Server, ServerCredentials } from '@grpc/grpc-js';
import { v4 as uuidv4 } from 'uuid';
import { AgentCard, Message, AGENT_CARD_PATH } from '@a2a-js/sdk';
import {
  AgentExecutor,
  RequestContext,
  ExecutionEventBus,
  DefaultRequestHandler,
  InMemoryTaskStore,
} from '@a2a-js/sdk/server';
import { agentCardHandler, jsonRpcHandler, restHandler, UserBuilder } from '@a2a-js/sdk/server/express';
import { grpcService, A2AService } from '@a2a-js/sdk/server/grpc';

// 1. Define your agent's identity card.
const helloAgentCard: AgentCard = {
  name: 'Hello Agent',
  description: 'A simple agent that says hello.',
  protocolVersion: '0.3.0',
  version: '0.1.0',
  url: 'http://localhost:4000/a2a/jsonrpc', // The public URL of your agent server
  skills: [{ id: 'chat', name: 'Chat', description: 'Say hello', tags: ['chat'] }],
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
class HelloExecutor implements AgentExecutor {
  async execute(requestContext: RequestContext, eventBus: ExecutionEventBus): Promise<void> {
    // Create a direct message response.
    const responseMessage: Message = {
      kind: 'message',
      messageId: uuidv4(),
      role: 'agent',
      parts: [{ kind: 'text', text: 'Hello, world!' }],
      // Associate the response with the incoming request's context.
      contextId: requestContext.contextId,
    };

    // Publish the message and signal that the interaction is finished.
    eventBus.publish(responseMessage);
    eventBus.finished();
  }

  // cancelTask is not needed for this simple, non-stateful agent.
  cancelTask = async (): Promise<void> => {};
}

// 3. Set up and run the server.
const agentExecutor = new HelloExecutor();
const requestHandler = new DefaultRequestHandler(
  helloAgentCard,
  new InMemoryTaskStore(),
  agentExecutor
);

const app = express();

app.use(`/${AGENT_CARD_PATH}`, agentCardHandler({ agentCardProvider: requestHandler }));
app.use('/a2a/jsonrpc', jsonRpcHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }));
app.use('/a2a/rest', restHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }));

app.listen(4000, () => {
  console.log(`🚀 Server started on http://localhost:4000`);
});

const server = new Server();
server.addService(A2AService, grpcService({
  requestHandler,
  userBuilder: UserBuilder.noAuthentication,
}));
server.bindAsync(`localhost:4001`, ServerCredentials.createInsecure(), () => {
  console.log(`🚀 Server started on localhost:4001`);
});