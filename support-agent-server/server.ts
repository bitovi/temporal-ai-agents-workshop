import dotenv from 'dotenv';
import express from 'express';
import { Server, ServerCredentials } from '@grpc/grpc-js';
import { v4 as uuidv4 } from 'uuid';
import {
  AgentCard,
  Message,
  AGENT_CARD_PATH,
  TextPart,
  TaskStatusUpdateEvent,
  TaskArtifactUpdateEvent,
  DataPart,
} from '@a2a-js/sdk';

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
import { getSupportTools } from './support-tools';
import { executeTool } from './tool-executor';

// Environment configuration
const HTTP_PORT = parseInt(process.env.HTTP_PORT || '4000', 10);
const GRPC_PORT = parseInt(process.env.GRPC_PORT || '4001', 10);
const HOST = process.env.HOST || 'localhost';

// In-memory store for conversation contexts (keyed by contextId)
const savedContexts = new Map<string, ConversationContext>();

const SYSTEM_PROMPT = `You are a customer support agent for Riot Games. You assist players with billing issues,
refunds, and account questions.

IMPORTANT: NEVER respond with a plain text question. Whenever you need ANY information from the
user (player ID, account details, clarification, etc.), you MUST use the request_information tool.
This ensures the conversation pauses properly until the user responds.

When a player reports a billing issue:
1. If you don't have the player's ID, use request_information to ask for it
2. Use lookup_account to find their account and check_billing_history to identify the problem
3. Before taking any action, use request_verification to ask the player to verify their identity.
   You MUST call request_verification — never skip this step.
4. Once you receive verification information in a follow-up message, use verify_identity to check it
5. If verified and a duplicate charge is found, use process_refund to issue the refund
6. Summarize the outcome clearly to the player

Do NOT reveal the stored email or payment details when asking for verification — only ask the
player to provide them. Do NOT process refunds before identity is verified.`;

/**
 * Sanitize tool input for display — strip sensitive identity fields.
 */
function sanitizeInput(input: Record<string, any>): Record<string, any> {
  const sanitized = { ...input };
  delete sanitized.email;
  delete sanitized.payment_last4;
  return sanitized;
}

// 1. Agent Card
const supportAgentCard: AgentCard = {
  name: 'Riot Games Support Agent',
  description: 'Handles billing inquiries, refunds, and account issues for Riot Games.',
  protocolVersion: '0.3.0',
  version: '0.1.0',
  url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`,
  skills: [
    { id: 'billing', name: 'Billing Support', description: 'Refunds, duplicate charges, payment issues', tags: ['billing', 'refunds'] },
    { id: 'account', name: 'Account Support', description: 'Account verification, password resets', tags: ['account'] },
  ],
  capabilities: {
    pushNotifications: false,
  },
  defaultInputModes: ['text'],
  defaultOutputModes: ['text', 'data'],
  additionalInterfaces: [
    { url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`, transport: 'JSONRPC' },
    { url: `http://${HOST}:${HTTP_PORT}/a2a/rest`, transport: 'HTTP+JSON' },
    { url: `${HOST}:${GRPC_PORT}`, transport: 'GRPC' },
  ],
};

// 2. Agent Executor
class SupportAgentExecutor implements AgentExecutor {
  async execute(requestContext: RequestContext, eventBus: ExecutionEventBus): Promise<void> {
    try {
      const contextId = requestContext.contextId;
      console.log(`[SupportAgent] Received message with context ID: ${contextId}`);

      // Extract user message text
      const userText = requestContext.userMessage.parts
        .filter((p): p is TextPart => p.kind === 'text')
        .map((p) => p.text)
        .join(' ');

      console.log(`[SupportAgent] User message: ${userText}`);

      // Detect follow-up: if there's a saved context for this contextId, this is a resume
      const isFollowUp = savedContexts.has(contextId);
      let context: ConversationContext;

      if (isFollowUp) {
        console.log(`[SupportAgent] Resuming conversation for context: ${contextId}`);
        context = savedContexts.get(contextId)!;
        // Append the new user message — safe because we injected an assistant summary before saving
        context.addUserMessage(userText);
      } else {
        console.log(`[SupportAgent] Starting new conversation for context: ${contextId}`);
        context = new ConversationContext();
        context.addUserMessage(userText);
      }

      // Publish initial task so the SDK's ResultManager can track subsequent status updates.
      // Without this, status-update events are silently dropped (the task doesn't exist in the
      // store yet) and blocking sendMessage returns null → "no task context found" error.
      const initialTask = {
        id: requestContext.taskId,
        contextId,
        status: {
          state: 'working' as const,
          message: {
            kind: 'message' as const,
            messageId: uuidv4(),
            role: 'agent' as const,
            parts: [{ kind: 'text' as const, text: 'Processing your request...' }],
          },
          timestamp: new Date().toISOString(),
        },
        history: [] as Message[],
        kind: 'task' as const,
      };
      eventBus.publish(initialTask);

      const tools = getSupportTools();
      const maxIterations = 10;
      let finalAnswer: string | undefined;

      // ReAct Loop
      for (let i = 0; i < maxIterations; i++) {
        console.log(`[ReAct] Iteration ${i + 1}/${maxIterations}`);

        const tokenCount = context.estimateTokenCount();
        console.log(`[ReAct] Estimated tokens: ${tokenCount}`);

        if (tokenCount > 12000) {
          console.warn(`[ReAct] Token limit exceeded (${tokenCount}), truncating oldest context`);
          context.truncateOldest();
        }

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

          for (const toolUse of response.toolUses) {
            console.log(`[ReAct] Tool: ${toolUse.name}`);

            // Emit working status for each tool call
            const toolWorkingStatus: TaskStatusUpdateEvent = {
              kind: 'status-update',
              taskId: requestContext.taskId,
              contextId,
              status: {
                state: 'working',
                message: {
                  kind: 'message',
                  messageId: uuidv4(),
                  role: 'agent',
                  parts: [{ kind: 'text', text: `→ ${toolUse.name}(${JSON.stringify(sanitizeInput(toolUse.input))})` }],
                },
              },
              final: false,
            };
            eventBus.publish(toolWorkingStatus);

            // ─── Sentinel: request_information / request_verification ───
            if (toolUse.name === 'request_information' || toolUse.name === 'request_verification') {
              console.log(`[ReAct] Sentinel hit: ${toolUse.name} — pausing for input`);

              // Add synthetic tool result to close the open toolUse block
              context.addToolResult(toolUse.toolUseId, JSON.stringify({
                status: "awaiting_verification",
                message: "User has been asked to provide identity verification.",
              }));

              // Inject an assistant-role summary so the follow-up user message alternates correctly
              context.addAssistantMessage([{
                text: "I've asked the user to verify their identity. Waiting for their response.",
              }]);

              // Save context for resume
              savedContexts.set(contextId, context);

              // Extract the verification question from input
              const verificationMessage = toolUse.input.message as string ||
                'Please verify your identity by providing the email on file or the last 4 digits of your payment method.';

              // Emit input-required status
              const inputRequiredStatus: TaskStatusUpdateEvent = {
                kind: 'status-update',
                taskId: requestContext.taskId,
                contextId,
                status: {
                  state: 'input-required',
                  message: {
                    kind: 'message',
                    messageId: uuidv4(),
                    role: 'agent',
                    parts: [{ kind: 'text', text: verificationMessage }],
                  },
                },
                final: true,
              };
              eventBus.publish(inputRequiredStatus);
              eventBus.finished();
              return; // Break out of executor entirely
            }

            // ─── Normal tool execution ───
            try {
              const result = executeTool(toolUse.name, toolUse.input);
              console.log(`[ReAct] Tool result length: ${result.length} chars`);

              // Emit artifact for process_refund
              if (toolUse.name === 'process_refund') {
                try {
                  const refundData = JSON.parse(result);
                  if (refundData.confirmationNumber) {
                    const artifactEvent: TaskArtifactUpdateEvent = {
                      kind: 'artifact-update',
                      taskId: requestContext.taskId,
                      contextId,
                      artifact: {
                        artifactId: uuidv4(),
                        name: 'Refund Receipt',
                        parts: [{ kind: 'data', data: refundData } as DataPart],
                      },
                    };
                    eventBus.publish(artifactEvent);
                  }
                } catch (parseErr) {
                  // Non-JSON result from process_refund — skip artifact emission
                }
              }

              context.addToolResult(toolUse.toolUseId, result);
            } catch (error) {
              console.error(`[ReAct] Tool execution failed:`, error);
              const errorResult = JSON.stringify({
                error: `Tool execution failed: ${error instanceof Error ? error.message : 'Unknown error'}`
              });
              context.addToolResult(toolUse.toolUseId, errorResult);
            }
          }

          continue;
        }

        // Empty response handling
        if (!response.text && (!response.toolUses || response.toolUses.length === 0)) {
          console.warn(`[ReAct] Empty response from Bedrock on iteration ${i + 1}`);
          if (i === 0) {
            continue;
          } else {
            finalAnswer = "I apologize, but I encountered an issue processing your request. Please try again.";
            break;
          }
        }
      }

      // Handle max iterations reached
      if (!finalAnswer) {
        console.warn(`[ReAct] Max iterations reached without final answer`);
        finalAnswer = "I've been working on your request but need more time. Please try rephrasing or providing more details.";
      }

      console.log(`[SupportAgent] Final answer ready`);

      // Clean up saved context on completion
      savedContexts.delete(contextId);

      // Publish completed status
      const completedStatus: TaskStatusUpdateEvent = {
        kind: 'status-update',
        taskId: requestContext.taskId,
        contextId,
        status: {
          state: 'completed',
          message: {
            kind: 'message',
            messageId: uuidv4(),
            role: 'agent',
            parts: [{ kind: 'text', text: finalAnswer }],
          },
        },
        final: true,
      };
      eventBus.publish(completedStatus);

      // Also publish as a message for clients expecting MessageEvent
      const responseMessage: Message = {
        kind: 'message',
        messageId: uuidv4(),
        role: 'agent',
        parts: [{ kind: 'text', text: finalAnswer }],
        contextId,
      };
      eventBus.publish(responseMessage);
      eventBus.finished();

    } catch (error) {
      console.error('[SupportAgent] Error processing request:', error);

      const errorMessage: Message = {
        kind: 'message',
        messageId: uuidv4(),
        role: 'agent',
        parts: [{ kind: 'text', text: 'I apologize, but I encountered an error. Please try again later.' }],
        contextId: requestContext.contextId,
      };

      eventBus.publish(errorMessage);
      eventBus.finished();
    }
  }

  cancelTask = async (): Promise<void> => {};
}

// 3. Set up and run the server
const agentExecutor = new SupportAgentExecutor();
const requestHandler = new DefaultRequestHandler(
  supportAgentCard,
  new InMemoryTaskStore(),
  agentExecutor
);

const app = express();

app.use(`/${AGENT_CARD_PATH}`, agentCardHandler({ agentCardProvider: requestHandler }));
app.use('/a2a/jsonrpc', jsonRpcHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }));
app.use('/a2a/rest', restHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }));

app.listen(HTTP_PORT, '0.0.0.0', () => {
  console.log(`🚀 HTTP Server started on http://0.0.0.0:${HTTP_PORT}`);
});

const server = new Server();
server.addService(A2AService, grpcService({
  requestHandler,
  userBuilder: UserBuilder.noAuthentication,
}));
server.bindAsync(`0.0.0.0:${GRPC_PORT}`, ServerCredentials.createInsecure(), () => {
  console.log(`🚀 gRPC Server started on 0.0.0.0:${GRPC_PORT}`);
});
