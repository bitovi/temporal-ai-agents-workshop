import {
  TextPart,
  TaskStatusUpdateEvent,
  TaskArtifactUpdateEvent,
  DataPart,
  Message,
} from "@a2a-js/sdk";
import {
  AgentExecutor,
  ExecutionEventBus,
  RequestContext,
} from "@a2a-js/sdk/server";
import { callBedrockWithTools } from "./bedrock-client";
import { ConversationContext } from "./conversation-context";
import { getSupportTools } from "./support-tools";
import { executeTool } from "./tool-executor";
import { v4 as uuidv4 } from "uuid";
import { SYSTEM_PROMPT } from "./prompt";
import { cleanFinalAnswer, sanitizeInput } from "./utils";

// In-memory store for conversation contexts (keyed by contextId)
const savedContexts = new Map<string, ConversationContext>();

// 2. Agent Executor
export class SupportAgentExecutor implements AgentExecutor {
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
