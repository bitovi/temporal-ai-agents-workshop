/**
 * Exercise 8 - Agent to Agent (TypeScript) — A2A Server Scaffold
 *
 * This file demonstrates how to create an A2A-compliant agent server using
 * the @a2a-js/sdk. It exposes:
 *   - Agent Card at /.well-known/agent-card.json
 *   - JSON-RPC endpoint for A2A communication
 *   - REST endpoint as an alternative transport
 *
 * TODO: Replace the placeholder processRequest() with real LLM integration
 * (e.g., Bedrock Converse API). See the support-agent-server/ for a complete
 * implementation with tool calling, multi-turn conversations, and artifacts.
 */

import express from 'express'
import { v4 as uuidv4 } from 'uuid'
import * as dotenv from 'dotenv'
import { AgentCard, TaskStatusUpdateEvent, TextPart, Message, AGENT_CARD_PATH } from '@a2a-js/sdk'
import {
  InMemoryTaskStore,
  AgentExecutor,
  RequestContext,
  ExecutionEventBus,
  DefaultRequestHandler,
} from '@a2a-js/sdk/server'
import {
  agentCardHandler,
  jsonRpcHandler,
  restHandler,
  UserBuilder,
} from '@a2a-js/sdk/server/express'

dotenv.config()

/**
 * ExampleAgentExecutor implements the A2A AgentExecutor interface.
 *
 * The execute() method is called when a message arrives. It should:
 *   1. Publish a 'working' status
 *   2. Process the request (call an LLM, run tools, etc.)
 *   3. Publish a 'completed' status with the final answer
 *
 * For multi-turn conversations, publish 'input-required' instead of
 * 'completed' and the caller will send a follow-up message.
 */
class ExampleAgentExecutor implements AgentExecutor {
  async execute(requestContext: RequestContext, eventBus: ExecutionEventBus): Promise<void> {
    const contextId = requestContext.contextId
    const taskId = requestContext.taskId

    // Extract user message text from A2A message parts
    const userText = requestContext.userMessage.parts
      .filter((p): p is TextPart => p.kind === 'text')
      .map((p) => p.text)
      .join(' ')

    console.log(`[ExampleAgent] Received: ${userText}`)

    // Publish initial task so the SDK can track status updates
    eventBus.publish({
      id: taskId,
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
    })

    try {
      // TODO: Replace with real LLM processing
      const responseText = `I received your message: "${userText}". This is a placeholder response. See support-agent-server/ for a complete implementation.`

      // Publish completed status with the response
      const completedStatus: TaskStatusUpdateEvent = {
        kind: 'status-update',
        taskId,
        contextId,
        status: {
          state: 'completed',
          message: {
            kind: 'message',
            messageId: uuidv4(),
            role: 'agent',
            parts: [{ kind: 'text', text: responseText }],
          },
        },
        final: true,
      }
      eventBus.publish(completedStatus)
      eventBus.finished()
    } catch (error: any) {
      eventBus.publish({
        kind: 'status-update' as const,
        taskId,
        contextId,
        status: {
          state: 'failed' as const,
          message: {
            kind: 'message' as const,
            messageId: uuidv4(),
            role: 'agent' as const,
            parts: [{ kind: 'text' as const, text: `Error: ${error.message}` }],
          },
        },
        final: true,
      })
      eventBus.finished()
    }
  }

  cancelTask = async (): Promise<void> => {}
}

// Agent Card — advertises this agent's capabilities to callers
const exampleAgentCard: AgentCard = {
  protocolVersion: '0.3.0',
  name: 'Example Agent',
  description: 'A scaffold example agent demonstrating A2A protocol structure.',
  url: 'http://localhost:41240/a2a/jsonrpc',
  version: '1.0.0',
  capabilities: {
    streaming: true,
    pushNotifications: false,
  },
  defaultInputModes: ['text'],
  defaultOutputModes: ['text'],
  skills: [
    {
      id: 'general_assistance',
      name: 'General Assistance',
      description: 'Provides general assistance and responds to user queries.',
      tags: ['general', 'assistance', 'example'],
      examples: ['Hello, how can you help me?', 'What can you do?'],
      inputModes: ['text'],
      outputModes: ['text'],
    },
  ],
}

async function main() {
  const agentExecutor = new ExampleAgentExecutor()
  const requestHandler = new DefaultRequestHandler(
    exampleAgentCard,
    new InMemoryTaskStore(),
    agentExecutor
  )

  const app = express()

  // Standard A2A endpoints
  app.use(`/${AGENT_CARD_PATH}`, agentCardHandler({ agentCardProvider: requestHandler }))
  app.use(
    '/a2a/jsonrpc',
    jsonRpcHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication })
  )
  app.use('/a2a/rest', restHandler({ requestHandler, userBuilder: UserBuilder.noAuthentication }))

  // Health check
  app.get('/health', (_req, res) => {
    res.json({ status: 'healthy', timestamp: new Date().toISOString() })
  })

  const PORT = process.env.PORT || 41240
  app.listen(PORT, () => {
    console.log(`[ExampleAgent] Server started on http://localhost:${PORT}`)
    console.log(`[ExampleAgent] Agent Card: http://localhost:${PORT}/.well-known/agent-card.json`)
  })
}

main().catch((error) => {
  console.error('Failed to start server:', error)
  process.exit(1)
})
