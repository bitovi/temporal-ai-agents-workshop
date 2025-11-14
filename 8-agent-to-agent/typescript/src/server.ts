import express, { Request, Response } from 'express'
import { v4 as uuidv4 } from 'uuid'
import * as dotenv from 'dotenv'

// Note: These imports would require adding the A2A SDK dependencies to package.json
// npm install @a2a-js/sdk
import {
  AgentCard,
  Task,
  TaskStatusUpdateEvent,
  TextPart,
  Message,
  TaskArtifactUpdateEvent,
} from '@a2a-js/sdk'
import {
  InMemoryTaskStore,
  TaskStore,
  AgentExecutor,
  RequestContext,
  ExecutionEventBus,
  DefaultRequestHandler,
  AgentExecutionEvent,
} from '@a2a-js/sdk/server'
import { A2AExpressApp } from '@a2a-js/sdk/server/express'

dotenv.config()

// Environment check
if (!process.env.OPENAI_API_KEY) {
  console.error('OPENAI_API_KEY environment variable is required')
  process.exit(1)
}

/**
 * ExampleAgentExecutor implements the agent's core logic.
 * Based on the Google A2A Coder Agent sample structure.
 */
class ExampleAgentExecutor implements AgentExecutor {
  private cancelledTasks = new Set<string>()

  public cancelTask = async (taskId: string, eventBus: ExecutionEventBus): Promise<void> => {
    this.cancelledTasks.add(taskId)
    // The execute loop is responsible for publishing the final state
  }

  async execute(requestContext: RequestContext, eventBus: ExecutionEventBus): Promise<void> {
    const userMessage = requestContext.userMessage
    const existingTask = requestContext.task

    // Determine IDs for the task and context
    const taskId = existingTask?.id || uuidv4()
    const contextId = userMessage.contextId || existingTask?.contextId || uuidv4()

    console.log(
      `[ExampleAgentExecutor] Processing message ${userMessage.messageId} for task ${taskId} (context: ${contextId})`
    )

    // 1. Publish initial Task event if it's a new task
    if (!existingTask) {
      const initialTask: Task = {
        kind: 'task',
        id: taskId,
        contextId: contextId,
        status: {
          state: 'submitted',
          timestamp: new Date().toISOString(),
        },
        history: [userMessage],
        metadata: userMessage.metadata,
        artifacts: [], // Initialize artifacts array
      }
      eventBus.publish(initialTask)
    }

    // 2. Publish "working" status update
    const workingStatusUpdate: TaskStatusUpdateEvent = {
      kind: 'status-update',
      taskId: taskId,
      contextId: contextId,
      status: {
        state: 'working',
        message: {
          kind: 'message',
          role: 'agent',
          messageId: uuidv4(),
          parts: [{ kind: 'text', text: 'Processing your request...' }],
          taskId: taskId,
          contextId: contextId,
        },
        timestamp: new Date().toISOString(),
      },
      final: false,
    }
    eventBus.publish(workingStatusUpdate)

    try {
      // 3. Get user input text
      const userText = userMessage.parts
        .filter((p): p is TextPart => p.kind === 'text')
        .map((p) => p.text)
        .join(' ')

      console.log(`[ExampleAgentExecutor] User input: ${userText}`)

      // Check if the request has been cancelled
      if (this.cancelledTasks.has(taskId)) {
        console.log(`[ExampleAgentExecutor] Request cancelled for task: ${taskId}`)
        const cancelledUpdate: TaskStatusUpdateEvent = {
          kind: 'status-update',
          taskId: taskId,
          contextId: contextId,
          status: {
            state: 'canceled',
            timestamp: new Date().toISOString(),
          },
          final: true,
        }
        eventBus.publish(cancelledUpdate)
        return
      }

      // 4. Process the request (placeholder for actual AI processing)
      // In a real implementation, this would call OpenAI API or other AI services
      const responseText = await this.processRequest(userText)

      // 5. Publish successful completion
      const completionUpdate: TaskStatusUpdateEvent = {
        kind: 'status-update',
        taskId: taskId,
        contextId: contextId,
        status: {
          state: 'completed',
          message: {
            kind: 'message',
            role: 'agent',
            messageId: uuidv4(),
            parts: [{ kind: 'text', text: responseText }],
            taskId: taskId,
            contextId: contextId,
          },
          timestamp: new Date().toISOString(),
        },
        final: true,
      }
      eventBus.publish(completionUpdate)

      console.log(`[ExampleAgentExecutor] Task ${taskId} completed successfully`)
    } catch (error: any) {
      console.error(`[ExampleAgentExecutor] Error processing task ${taskId}: `, error)

      const errorUpdate: TaskStatusUpdateEvent = {
        kind: 'status-update',
        taskId: taskId,
        contextId: contextId,
        status: {
          state: 'failed',
          message: {
            kind: 'message',
            role: 'agent',
            messageId: uuidv4(),
            parts: [{ kind: 'text', text: `Agent error: ${error.message}` }],
            taskId: taskId,
            contextId: contextId,
          },
          timestamp: new Date().toISOString(),
        },
        final: true,
      }
      eventBus.publish(errorUpdate)
    }
  }

  private async processRequest(userText: string): Promise<string> {
    // Placeholder for actual AI processing
    // In a real implementation, this would integrate with:
    // - OpenAI API
    // - Other LLM services
    // - Custom business logic

    // Simulate processing time
    await new Promise((resolve) => setTimeout(resolve, 1000))

    return `I received your message: "${userText}". This is a placeholder response from the Example Agent. In a real implementation, this would be processed by an AI model.`
  }
}

// Agent Card definition - describes the agent's capabilities
const exampleAgentCard: AgentCard = {
  protocolVersion: '1.0',
  name: 'Example Agent',
  description: 'A scaffold example agent demonstrating A2A protocol structure.',
  url: 'http://localhost:41240/',
  provider: {
    organization: 'Bitovi Workshop',
    url: 'https://bitovi.com/',
  },
  version: '1.0.0',
  capabilities: {
    streaming: true,
    pushNotifications: false,
    stateTransitionHistory: true,
  },
  securitySchemes: undefined,
  security: undefined,
  defaultInputModes: ['text'],
  defaultOutputModes: ['text', 'task-status'],
  skills: [
    {
      id: 'general_assistance',
      name: 'General Assistance',
      description: 'Provides general assistance and responds to user queries.',
      tags: ['general', 'assistance', 'example'],
      examples: ['Hello, how can you help me?', 'What can you do?', 'Process this request for me.'],
      inputModes: ['text'],
      outputModes: ['text', 'task-status'],
    },
  ],
  supportsAuthenticatedExtendedCard: false,
}

// Placeholder implementations for missing SDK components
class MockTaskStore {
  private tasks = new Map<string, Task>()

  async getTask(taskId: string): Promise<Task | undefined> {
    return this.tasks.get(taskId)
  }

  async saveTask(task: Task): Promise<void> {
    this.tasks.set(task.id, task)
  }
}

class MockExecutionEventBus implements ExecutionEventBus {
  publish(event: any): void {
    console.log('[MockEventBus] Event published:', JSON.stringify(event, null, 2))
  }
  finished(): void {
    console.log('[MockEventBus] Event bus finished')
  }
  off(eventName: 'event' | 'finished', listener: (event: AgentExecutionEvent) => void): this {
    return this
  }
  on(eventName: 'event' | 'finished', listener: (event: AgentExecutionEvent) => void): this {
    return this
  }
  once(eventName: 'event' | 'finished', listener: (event: AgentExecutionEvent) => void): this {
    return this
  }
  removeAllListeners(eventName?: 'event' | 'finished'): this {
    return this
  }
}

class MockDefaultRequestHandler {
  constructor(
    private agentCard: AgentCard,
    private taskStore: MockTaskStore,
    private agentExecutor: AgentExecutor
  ) {}

  async handleRequest(req: express.Request, res: express.Response): Promise<void> {
    // This is a simplified mock implementation
    // Real A2A SDK would handle JSON-RPC 2.0 protocol properly

    if (req.path === '/.well-known/agent-card.json') {
      res.json(this.agentCard)
      return
    }

    // Mock message handling
    const mockMessage: Message = {
      kind: 'message',
      role: 'user',
      messageId: uuidv4(),
      parts: [{ kind: 'text', text: req.body?.message || 'Hello from mock client' }],
      contextId: uuidv4(),
    }

    const mockContext: RequestContext = {
      userMessage: mockMessage,
      contextId: mockMessage.contextId!,
      taskId: uuidv4(),
      referenceTasks: [],
      task: undefined,
    }

    const eventBus = new MockExecutionEventBus()

    try {
      await this.agentExecutor.execute(mockContext, eventBus)
      res.json({ status: 'success', message: 'Task submitted' })
    } catch (error: unknown) {
      const err = error as Error
      res.status(500).json({ status: 'error', message: err.message })
    }
  }
}

async function main() {
  console.log('Starting Example A2A Agent Server...')
  console.log(
    'Note: This is a scaffold implementation. To use real A2A functionality, install @a2a-js/sdk'
  )

  // 1. Create TaskStore (mock implementation)
  const taskStore = new MockTaskStore()

  // 2. Create AgentExecutor
  const agentExecutor: AgentExecutor = new ExampleAgentExecutor()

  // 3. Create DefaultRequestHandler (mock implementation)
  const requestHandler = new MockDefaultRequestHandler(exampleAgentCard, taskStore, agentExecutor)

  // 4. Create Express app and setup routes
  const app = express()
  app.use(express.json())

  // Agent card endpoint (standard A2A endpoint)
  app.get('/.well-known/agent-card.json', async (req, res) => {
    await requestHandler.handleRequest(req, res)
  })

  // Mock message endpoint for testing
  app.post('/message', async (req, res) => {
    await requestHandler.handleRequest(req, res)
  })

  // Health check endpoint
  app.get('/health', (req, res) => {
    res.json({ status: 'healthy', timestamp: new Date().toISOString() })
  })

  // 5. Start the server
  const PORT = process.env.PORT || 41240
  app.listen(PORT, () => {
    console.log(`[ExampleAgent] Server started on http://localhost:${PORT}`)
    console.log(`[ExampleAgent] Agent Card: http://localhost:${PORT}/.well-known/agent-card.json`)
    console.log(`[ExampleAgent] Test endpoint: http://localhost:${PORT}/message`)
    console.log('[ExampleAgent] Press Ctrl+C to stop the server')
    console.log('\nTo implement real A2A functionality:')
    console.log('1. npm install @a2a-js/sdk')
    console.log('2. Replace mock implementations with real SDK components')
    console.log('3. Uncomment and use the real A2A imports at the top of this file')
  })
}

// Handle graceful shutdown
process.on('SIGINT', () => {
  console.log('\n[ExampleAgent] Shutting down gracefully...')
  process.exit(0)
})

// Start the server
main().catch((error) => {
  console.error('Failed to start server:', error)
  process.exit(1)
})
