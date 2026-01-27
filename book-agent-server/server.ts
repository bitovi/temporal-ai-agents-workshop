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
import { Tool } from '@aws-sdk/client-bedrock-runtime';
import { ConversationContext } from './conversation-context';
import { callBedrockWithTools } from './bedrock-client';
import { getGutendexTools } from './gutendex-tools';
import { executeTool } from './tool-executor';

/**
 * Generate system prompt dynamically from tool definitions
 */
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
5. Do NOT use your general knowledge about books. Rely exclusively on the book data you can find from the gutendex api tools.

You have access to these tools:
${toolDescriptions}

Be thorough but concise. If search results are truncated, mention the total count available.
When tool results indicate errors or no matches, explain this clearly to the user and suggest alternatives.`;
}

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
  async execute(requestContext: RequestContext, eventBus: ExecutionEventBus): Promise<void> {
    try {
      console.log(`[BookAgent] Received message with context ID: ${requestContext.contextId}`);
      
      // Extract user message text from requestContext.userMessage.parts
      const userText = requestContext.userMessage.parts
        .filter((p): p is TextPart => p.kind === 'text')
        .map((p) => p.text)
        .join(' ');

      console.log(`[BookAgent] User message: ${userText}`);

      // Initialize conversation context and tools
      const context = new ConversationContext();
      context.addUserMessage(userText);

      const tools = getGutendexTools();
      const systemPrompt = generateSystemPrompt(tools);
      const maxIterations = 10;
      let finalAnswer: string | undefined;

      // ReAct Loop
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
          systemPrompt,
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

      console.log(`[BookAgent] Final answer ready`);

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