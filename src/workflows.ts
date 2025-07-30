import { proxyActivities } from '@temporalio/workflow'
import * as activities from './activities'
import { ChatCompletionMessageParam } from 'openai/resources/chat/completions'

const { thought, action, executeToolCall, observation } = proxyActivities<typeof activities>({
  startToCloseTimeout: '1 minute',
  retry: {
    backoffCoefficient: 1,
    initialInterval: '3 seconds',
  },
})

export async function agentWorkflow(question: string): Promise<string> {
  let done = false

  const messages: ChatCompletionMessageParam[] = [
    {
      role: 'user',
      content: question,
    },
  ]

  let response = ''
  while (!done) {
    const thoughtResponse = await thought(messages)
    if (thoughtResponse.response !== null) {
      response = thoughtResponse.response
    }

    messages.push({
      role: 'assistant',
      content: response,
    })

    const actionResponse = await action(messages)

    if (actionResponse.toolCall) {
      const toolCallResponse = await executeToolCall(actionResponse.toolCall)
      messages.push({
        role: 'assistant',
        content: toolCallResponse,
      })
    }

    const nextUserMessage = await observation(messages)
    done = nextUserMessage.done
    if (nextUserMessage.response !== null) {
      response = nextUserMessage.response
    }

    messages.push({
      role: 'user',
      content: response,
    })
  }

  return response
}
