import dotenv from 'dotenv'
import { OpenAI as OpenAIClient } from 'openai'
import { ChatCompletionMessageParam } from 'openai/resources'

dotenv.config()

export async function completionRequest(messages: ChatCompletionMessageParam[]): Promise<string> {
  const client = new OpenAIClient({
    apiKey: process.env.OPENAI_API_KEY,
    baseURL: process.env.OPENAI_API_BASE,
  })

  const result = await client.chat.completions.create({
    model: process.env.OPENAI_MODEL as string,
    messages: messages,
  })

  if (!result.choices || result.choices.length === 0) {
    throw new Error('No response from OpenAI API')
  }

  if (!result.choices[0].message || !result.choices[0].message.content) {
    throw new Error('No content in OpenAI response')
  }

  console.log(`OpenAI Response: ${result.choices[0].message.content}`)
  return result.choices[0].message.content
}
