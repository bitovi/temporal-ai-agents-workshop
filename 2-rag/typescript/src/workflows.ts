import { proxyActivities } from '@temporalio/workflow'
import type * as activities from './activities'
import { ChatCompletionMessageParam } from 'openai/resources'
import { documentChunksToString } from './utils'

const { semanticSearchDocuments, completionRequest, embeddingsRequest, validateQdrantCollection } =
  proxyActivities<typeof activities>({
    startToCloseTimeout: '1 minute',
    retry: {
      backoffCoefficient: 1,
      initialInterval: '3 seconds',
      maximumAttempts: 3,
    },
  })

type RetrievalAugmentedGenerationInput = {
  query: string
}

export async function retrievalAugmentedGenerationWorkflow(
  input: RetrievalAugmentedGenerationInput
): Promise<string> {
  await validateQdrantCollection()
  const embedding = await embeddingsRequest(input.query)
  const documents = await semanticSearchDocuments(embedding, 5, 0.8)

  const prompt: ChatCompletionMessageParam[] = []
  prompt.push({
    role: 'system',
    content: `You are a conversational assistant named 'Aurora' that is helpful, creative, clever, and friendly.
				Your job is to assist users in a variety of tasks including answering questions, providing
				information, and engaging in casual conversation. You should respond in concise paragraphs, seperated by newlines, to maintain readability and clarity.`,
  })

  prompt.push({
    role: 'developer',
    content: `Here are some documents fetched from a vector database that may be relevant to the user's query:\n ${documentChunksToString(documents)}`,
  })

  prompt.push({
    role: 'user',
    content: input.query,
  })

  const response = await completionRequest(prompt)

  return response
}

type DocumentEmbeddingInput = {
  urls: string[]
}

export async function documentEmbeddingWorkflow(input: DocumentEmbeddingInput): Promise<void> {}
