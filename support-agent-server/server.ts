/**
 * Riot Games Support Agent — A2A (Agent-to-Agent) Server
 *
 * This is a remote A2A agent that handles billing inquiries, refunds, and
 * account issues. It demonstrates:
 *
 *   1. Agent Card — advertises capabilities at .well-known/agent-card.json
 *   2. Multi-turn conversations — uses `input-required` state to pause and
 *      ask the caller for identity verification, then resumes on follow-up
 *   3. Artifacts — emits structured refund receipts as DataPart artifacts
 *   4. Opacity — the calling agent can’t see this agent’s internal tools or
 *      reasoning; it only sees status updates and the final answer
 *
 * Transports: JSON-RPC (port 4000), REST (port 4000), gRPC (port 4001)
 */

import dotenv from "dotenv";
import express from "express";
import { Server, ServerCredentials } from "@grpc/grpc-js";
import { v4 as uuidv4 } from "uuid";
import {
  AgentCard,
  Message,
  AGENT_CARD_PATH,
  TextPart,
  TaskStatusUpdateEvent,
  TaskArtifactUpdateEvent,
  DataPart,
} from "@a2a-js/sdk";

dotenv.config();
import {
  AgentExecutor,
  RequestContext,
  ExecutionEventBus,
  DefaultRequestHandler,
  InMemoryTaskStore,
} from "@a2a-js/sdk/server";
import {
  agentCardHandler,
  jsonRpcHandler,
  restHandler,
  UserBuilder,
} from "@a2a-js/sdk/server/express";
import { grpcService, A2AService } from "@a2a-js/sdk/server/grpc";
import { ConversationContext } from "./conversation-context";
import { callBedrockWithTools } from "./bedrock-client";
import { getSupportTools } from "./support-tools";
import { executeTool } from "./tool-executor";

// Environment configuration
const HTTP_PORT = parseInt(process.env.HTTP_PORT || "4000", 10);
const GRPC_PORT = parseInt(process.env.GRPC_PORT || "4001", 10);
const HOST = process.env.HOST || "localhost";

// In-memory store for conversation contexts (keyed by contextId)
const savedContexts = new Map<string, ConversationContext>();

const SYSTEM_PROMPT = `You are a customer support agent for Riot Games. You assist players with billing issues,
refunds, and account questions.

Whenever you need information from the user (player ID, account details, clarification, etc.),
use the request_information tool rather than responding with a plain text question. This ensures
the conversation pauses properly until the user responds.

General approach for billing issues:
1. If you don't have the player's ID, use request_information to ask for it.
2. Use lookup_account and check_billing_history to understand the situation.
3. Before taking any account action, use request_verification to verify the player's identity.
4. When the user provides verification info, use verify_identity to check it.
5. If verified and a billing issue is confirmed, use offer_resolution_options to let the player
   choose how they'd like it resolved.
6. Use apply_resolution with the option the user selects.
7. Summarize the outcome.

Do NOT reveal stored email or payment details when asking for verification — let the player
provide them. Identity should be verified before applying resolutions.

Your final response to the user should contain ONLY the customer-facing message, with no internal
reasoning or meta-commentary.`;

/**
 * Strip chain-of-thought reasoning that Bedrock sometimes leaks into the final answer.
 */
function cleanFinalAnswer(text: string): string {
  // let cleaned = text;

  // // Strip <thinking>...</thinking> blocks
  // cleaned = cleaned.replace(/<thinking>[\s\S]*?<\/thinking>/gi, "").trim();

  // // Strip everything before preamble phrases that signal the end of internal reasoning
  // const preamblePatterns = [
  //   /let['\u2019]s respond\.?\s*/i,
  //   /here(?:'s| is) (?:my |the )?(?:final )?response[.:]?\s*/i,
  //   /(?:my |the )?(?:final )?(?:response|answer) (?:is|would be|should be)[.:]?\s*/i,
  // ];
  // for (const pattern of preamblePatterns) {
  //   const idx = cleaned.search(pattern);
  //   if (idx !== -1) {
  //     cleaned = cleaned.substring(idx).replace(pattern, "").trim();
  //     break;
  //   }
  // }

  // // If there's a greeting (Hi/Hello/Dear/Hey Name) after a block of meta-commentary,
  // // extract from the greeting onward
  // const greetingMatch = cleaned.match(
  //   /(?:^|\n)((?:Hi|Hello|Dear|Hey)\s+\w[\s\S]*)/im,
  // );
  // if (
  //   greetingMatch &&
  //   greetingMatch.index !== undefined &&
  //   greetingMatch.index > 80
  // ) {
  //   cleaned = greetingMatch[1].trim();
  // }

  // // Strip leading lines that are clearly meta-commentary (e.g. "The conversation:...",
  // // "Now need to...", "Should output...", "Given guidelines...")
  // cleaned = cleaned
  //   .replace(
  //     /^(?:(?:the (?:conversation|assistant|user)|now (?:need|let)|should (?:output|provide|respond)|given (?:guidelines|instructions|the)|also |note:|important:)[^\n]*\n?)+/im,
  //     "",
  //   )
  //   .trim();

  // // Strip leading emoji/symbol noise, horizontal rules, and separator lines
  // cleaned = cleaned
  //   .replace(
  //     /^[\s\u2705\u274C\u26A1\u{1F50D}\u{1F9E0}\u{1F4E4}\u2B05\u2699\u{1F512}\u{1F4C4}\u2713\u00D7*#\-\u2500\u2014=|]+\s*/gu,
  //     "",
  //   )
  //   .trim();

  return text.trim();
}

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
  name: "Riot Games Support Agent",
  description:
    "Handles billing inquiries, refunds, and account issues for Riot Games.",
  protocolVersion: "0.3.0",
  version: "0.1.0",
  url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`,
  skills: [
    {
      id: "billing",
      name: "Billing Support",
      description: "Refunds, duplicate charges, payment issues",
      tags: ["billing", "refunds"],
    },
    {
      id: "account",
      name: "Account Support",
      description: "Account verification, password resets",
      tags: ["account"],
    },
  ],
  capabilities: {
    pushNotifications: false,
    streaming: true,
  },
  defaultInputModes: ["text"],
  defaultOutputModes: ["text", "data"],
  additionalInterfaces: [
    { url: `http://${HOST}:${HTTP_PORT}/a2a/jsonrpc`, transport: "JSONRPC" },
    { url: `http://${HOST}:${HTTP_PORT}/a2a/rest`, transport: "HTTP+JSON" },
    { url: `${HOST}:${GRPC_PORT}`, transport: "GRPC" },
  ],
};

// 2. Agent Executor
class SupportAgentExecutor implements AgentExecutor {
  async execute(
    requestContext: RequestContext,
    eventBus: ExecutionEventBus,
  ): Promise<void> {
    try {
      const contextId = requestContext.contextId;
      console.log(
        `[SupportAgent] Received message with context ID: ${contextId}`,
      );

      // Extract user message text
      const userText = requestContext.userMessage.parts
        .filter((p): p is TextPart => p.kind === "text")
        .map((p) => p.text)
        .join(" ");

      console.log(`[SupportAgent] User message: ${userText}`);

      // Detect follow-up: if there's a saved context for this contextId, this is a resume
      const isFollowUp = savedContexts.has(contextId);
      let context: ConversationContext;

      if (isFollowUp) {
        console.log(
          `[SupportAgent] Resuming conversation for context: ${contextId}`,
        );
        context = savedContexts.get(contextId)!;
        // Append the new user message — safe because we injected an assistant summary before saving
        context.addUserMessage(userText);
      } else {
        console.log(
          `[SupportAgent] Starting new conversation for context: ${contextId}`,
        );
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
          state: "working" as const,
          message: {
            kind: "message" as const,
            messageId: uuidv4(),
            role: "agent" as const,
            parts: [
              { kind: "text" as const, text: "Processing your request..." },
            ],
          },
          timestamp: new Date().toISOString(),
        },
        history: [] as Message[],
        kind: "task" as const,
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
          console.warn(
            `[ReAct] Token limit exceeded (${tokenCount}), truncating oldest context`,
          );
          context.truncateOldest();
        }

        const response = await callBedrockWithTools(
          context.getMessages(),
          SYSTEM_PROMPT,
          tools,
        );

        // Check for final answer (text response without tool use)
        if (
          response.text &&
          (!response.toolUses || response.toolUses.length === 0)
        ) {
          console.log(`[ReAct] Final answer ready, exiting loop`);
          finalAnswer = response.text;
          break;
        }

        // Handle tool use requests
        if (response.toolUses && response.toolUses.length > 0) {
          console.log(
            `[ReAct] Processing ${response.toolUses.length} tool request(s)`,
          );

          // Add assistant's tool use request to context
          const toolUseBlocks = response.toolUses.map((tu) => ({
            toolUse: {
              toolUseId: tu.toolUseId,
              name: tu.name,
              input: tu.input,
            },
          }));
          context.addAssistantMessage(toolUseBlocks);

          for (const toolUse of response.toolUses) {
            console.log(`[ReAct] Tool: ${toolUse.name}`);

            // Emit working status for each tool call
            const toolWorkingStatus: TaskStatusUpdateEvent = {
              kind: "status-update",
              taskId: requestContext.taskId,
              contextId,
              status: {
                state: "working",
                message: {
                  kind: "message",
                  messageId: uuidv4(),
                  role: "agent",
                  parts: [
                    {
                      kind: "text",
                      text: `→ ${toolUse.name}(${JSON.stringify(sanitizeInput(toolUse.input))})`,
                    },
                  ],
                },
              },
              final: false,
            };
            eventBus.publish(toolWorkingStatus);

            // ─── Sentinel: request_information / request_verification / offer_resolution_options ───
            if (
              toolUse.name === "request_information" ||
              toolUse.name === "request_verification" ||
              toolUse.name === "offer_resolution_options"
            ) {
              console.log(
                `[ReAct] Sentinel hit: ${toolUse.name} — pausing for input`,
              );

              // Add synthetic tool result to close the open toolUse block
              context.addToolResult(
                toolUse.toolUseId,
                JSON.stringify({
                  status: "awaiting_user_response",
                  message: `User has been asked via ${toolUse.name}. Waiting for their response.`,
                }),
              );

              // Inject an assistant-role summary so the follow-up user message alternates correctly
              context.addAssistantMessage([
                {
                  text: `I've asked the user for input via ${toolUse.name}. Waiting for their response.`,
                },
              ]);

              // Save context for resume
              savedContexts.set(contextId, context);

              // Extract the verification question from input
              const verificationMessage =
                (toolUse.input.message as string) ||
                "Please verify your identity by providing the email on file or the last 4 digits of your payment method.";

              // Emit input-required status
              const inputRequiredStatus: TaskStatusUpdateEvent = {
                kind: "status-update",
                taskId: requestContext.taskId,
                contextId,
                status: {
                  state: "input-required",
                  message: {
                    kind: "message",
                    messageId: uuidv4(),
                    role: "agent",
                    parts: [{ kind: "text", text: verificationMessage }],
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

              // ─── Verification failure → emit failed status and terminate ───
              if (toolUse.name === "verify_identity") {
                try {
                  const verifyResult = JSON.parse(result);
                  if (verifyResult.verified === false) {
                    console.log(
                      `[ReAct] Identity verification failed — terminating with failed state`,
                    );

                    const failedStatus: TaskStatusUpdateEvent = {
                      kind: "status-update",
                      taskId: requestContext.taskId,
                      contextId,
                      status: {
                        state: "failed",
                        message: {
                          kind: "message",
                          messageId: uuidv4(),
                          role: "agent",
                          parts: [
                            {
                              kind: "text",
                              text: `Identity verification failed: ${verifyResult.reason || "Provided credentials do not match our records."}`,
                            },
                          ],
                        },
                      },
                      final: true,
                    };
                    eventBus.publish(failedStatus);
                    savedContexts.delete(contextId);
                    eventBus.finished();
                    return;
                  }
                } catch (_parseErr) {
                  // Non-JSON response — continue normally
                }
              }

              // Emit artifact for apply_resolution
              if (toolUse.name === "apply_resolution") {
                try {
                  const resolutionData = JSON.parse(result);
                  if (resolutionData.confirmationNumber) {
                    const artifactEvent: TaskArtifactUpdateEvent = {
                      kind: "artifact-update",
                      taskId: requestContext.taskId,
                      contextId,
                      artifact: {
                        artifactId: uuidv4(),
                        name:
                          resolutionData.resolutionType || "Resolution Receipt",
                        parts: [
                          { kind: "data", data: resolutionData } as DataPart,
                        ],
                      },
                    };
                    eventBus.publish(artifactEvent);
                  }
                } catch (parseErr) {
                  // Non-JSON result — skip artifact emission
                }
              }

              context.addToolResult(toolUse.toolUseId, result);
            } catch (error) {
              console.error(`[ReAct] Tool execution failed:`, error);
              const errorResult = JSON.stringify({
                error: `Tool execution failed: ${error instanceof Error ? error.message : "Unknown error"}`,
              });
              context.addToolResult(toolUse.toolUseId, errorResult);
            }
          }

          continue;
        }

        // Empty response handling
        if (
          !response.text &&
          (!response.toolUses || response.toolUses.length === 0)
        ) {
          console.warn(
            `[ReAct] Empty response from Bedrock on iteration ${i + 1}`,
          );
          if (i === 0) {
            continue;
          } else {
            finalAnswer =
              "I apologize, but I encountered an issue processing your request. Please try again.";
            break;
          }
        }
      }

      // Handle max iterations reached
      if (!finalAnswer) {
        console.warn(`[ReAct] Max iterations reached without final answer`);
        finalAnswer =
          "I've been working on your request but need more time. Please try rephrasing or providing more details.";
      }

      console.log(`[SupportAgent] Final answer ready`);

      // Strip any chain-of-thought reasoning that leaked into the final answer
      finalAnswer = cleanFinalAnswer(finalAnswer);

      // Clean up saved context on completion
      savedContexts.delete(contextId);

      // Publish completed status
      const completedStatus: TaskStatusUpdateEvent = {
        kind: "status-update",
        taskId: requestContext.taskId,
        contextId,
        status: {
          state: "completed",
          message: {
            kind: "message",
            messageId: uuidv4(),
            role: "agent",
            parts: [{ kind: "text", text: finalAnswer }],
          },
        },
        final: true,
      };
      eventBus.publish(completedStatus);

      // Also publish as a message for clients expecting MessageEvent
      const responseMessage: Message = {
        kind: "message",
        messageId: uuidv4(),
        role: "agent",
        parts: [{ kind: "text", text: finalAnswer }],
        contextId,
      };
      eventBus.publish(responseMessage);
      eventBus.finished();
    } catch (error) {
      console.error("[SupportAgent] Error processing request:", error);

      const errorMessage: Message = {
        kind: "message",
        messageId: uuidv4(),
        role: "agent",
        parts: [
          {
            kind: "text",
            text: "I apologize, but I encountered an error. Please try again later.",
          },
        ],
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
  agentExecutor,
);

const app = express();

app.use(
  `/${AGENT_CARD_PATH}`,
  agentCardHandler({ agentCardProvider: requestHandler }),
);
app.use(
  "/a2a/jsonrpc",
  jsonRpcHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }),
);
app.use(
  "/a2a/rest",
  restHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }),
);

app.listen(HTTP_PORT, "0.0.0.0", () => {
  console.log(`🚀 HTTP Server started on http://0.0.0.0:${HTTP_PORT}`);
});

const server = new Server();
server.addService(
  A2AService,
  grpcService({
    requestHandler,
    userBuilder: UserBuilder.noAuthentication,
  }),
);
server.bindAsync(
  `0.0.0.0:${GRPC_PORT}`,
  ServerCredentials.createInsecure(),
  () => {
    console.log(`🚀 gRPC Server started on 0.0.0.0:${GRPC_PORT}`);
  },
);
