import dotenv from 'dotenv'
import { OpenAI } from 'openai'
import {
  ChatCompletionMessageParam,
  ChatCompletionTool,
  ChatCompletionMessageToolCall,
} from 'openai/resources/chat/completions'
import { toolDefinitions, toolFunctions } from './tools'
import { z } from 'zod'
import { zodResponseFormat } from 'openai/helpers/zod'
import { ChatModel } from 'openai/resources'

dotenv.config()

export type Message = ChatCompletionMessageParam
type Tool = ChatCompletionTool

type ModelResponse = {
  response: string | null
  toolCall: ChatCompletionMessageToolCall | null
  done: boolean
}

export async function executeToolCall(toolCall: ChatCompletionMessageToolCall): Promise<string> {
  console.log('Executing tool call:', toolCall)
  const fn = toolFunctions[toolCall.function.name]
  if (!fn) {
    throw new Error(`Tool function ${toolCall.function.name} not found`)
  }
  return fn(JSON.parse(toolCall.function.arguments))
}

export async function thought(history: Message[]): Promise<ModelResponse> {
  const openai = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY,
    baseURL: process.env.OPENAI_API_BASE,
  })

  const thoughtPrompt = `
You are a Reacting and Acting agent. This is the Thought step. You should output a response that reflects your thoughts based on the conversation history.
You should output the steps that you think will be needed next in order to answer the users question.

In the next (Action) step you will have access to the following tools: ${Object.keys(toolDefinitions).join(', ')}. You can take these into account when planning your next steps.
`
  const response = await openai.chat.completions.create({
    model: process.env.OPENAI_MODEL as ChatModel,
    messages: [
      ...history,
      {
        role: 'user',
        content: thoughtPrompt,
      },
    ],
  })
  const responseMessage = response.choices[0].message

  return {
    response: responseMessage.content,
    done: false,
    toolCall: null,
  }
}

export async function action(history: Message[]): Promise<ModelResponse> {
  const openai = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY,
    baseURL: process.env.OPENAI_API_BASE,
  })

  const actionPrompt = `You are a Reacting and Acting agent. This is the Action step. You should call a tool that will help fetch the information needed based on the previous Thought step and conversation history.
  You can call one tool at a time.`

  const response = await openai.chat.completions.create({
    model: process.env.OPENAI_MODEL as ChatModel,
    messages: [
      ...history,
      {
        role: 'user',
        content: actionPrompt,
      },
    ],
    tools: toolDefinitions,
    tool_choice: 'auto',
    parallel_tool_calls: false,
  })
  const responseMessage = response.choices[0].message
  const toolCall = responseMessage.tool_calls?.[0] || null

  return {
    response: '',
    done: false,
    toolCall: toolCall,
  }
}

export async function observation(history: Message[]): Promise<ModelResponse> {
  const openai = new OpenAI({
    apiKey: process.env.OPENAI_API_KEY,
    baseURL: process.env.OPENAI_API_BASE,
  })

  const observationPrompt = `
You are a Reacting and Acting agent. This is the Observation step. You should output a response that reflects your observations based on the conversation history and the latest tool call result.
If you are not confident that the final answer has been determined you should set "done" to false and continue the conversation for another round, this will allow for another planning (Thought) and execution (Action) step.
If you are confident that you have reached the final answer, you must set the "done" property to true and output the final answer in the "response" property.`

  const completionResponse = await openai.chat.completions.create({
    model: process.env.OPENAI_MODEL as ChatModel,
    messages: [
      ...history,
      {
        role: 'user',
        content: observationPrompt,
      },
    ],
    response_format: zodResponseFormat(
      z.object({
        response: z.string().nullable(),
        done: z.boolean(),
      }),
      'response'
    ),
  })

  const responseMessage = completionResponse.choices[0].message
  const { response, done } = JSON.parse(responseMessage.content || '') as {
    response: string | null
    done: boolean
  }

  return {
    response,
    done,
    toolCall: null,
  }
}
