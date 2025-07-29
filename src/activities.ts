import dotenv from 'dotenv'
import { Ollama } from 'ollama'

dotenv.config()

export async function helloActivity(): Promise<string> {
  const ollama = new Ollama({ host: process.env.OLLAMA_HOST_AND_PORT })

  const messages = [
    { role: 'system', content: 'You are a helpful assistant.' },
    { role: 'user', content: 'Tell me a short story about a brave knight.' },
  ]

  const chatResponse = await ollama.chat({
    model: 'gemma3n:e4b',
    messages: messages,
    stream: true,
  })

  let response = ''
  for await (const chunk of chatResponse) {
    if (chunk.message?.content) {
      process.stdout.write(chunk.message.content)
      response += chunk.message.content
    }
  }
  return response
}
