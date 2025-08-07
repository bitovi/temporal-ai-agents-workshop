import dotenv from 'dotenv'
import { OpenAI as OpenAIClient } from 'openai'
import { ChatCompletionMessageParam } from 'openai/resources'
import { QdrantClient } from '@qdrant/js-client-rest'

dotenv.config()

const COLLECTION_NAME = 'rag-knowledge-base'
const COLLECTION_WIDTH = 3072
const EMBEDDING_MODEL = 'text-embedding-3-large'

export async function semanticSearchDocuments(
  embedding: number[],
  limit: number,
  threshold: number
): Promise<string[]> {
  const client = new QdrantClient({
    url: `http://${process.env.QDRANT_HOST}:${process.env.QDRANT_PORT_HTTP}`,
  })

  const results = await client.search(COLLECTION_NAME, {
    vector: embedding,
    limit: limit,
    score_threshold: threshold,
    with_payload: true,
  })

  if (!results || results.length === 0) {
    return []
  }

  // Extract document chunks from search results
  const documents: string[] = results.map((result) => {
    return result.payload!.text as string
  })

  return documents
}

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

export async function embeddingsRequest(input: string): Promise<number[]> {
  // Convert search term to a vector using OpenAI Embeddings
  const openaiClient = new OpenAIClient({
    apiKey: process.env.OPENAI_API_KEY,
    baseURL: process.env.OPENAI_API_BASE,
  })

  const embeddingResponse = await openaiClient.embeddings.create({
    model: EMBEDDING_MODEL,
    input: input,
  })

  if (!embeddingResponse.data || embeddingResponse.data.length === 0) {
    throw new Error('No embeddings returned from OpenAI API')
  }

  return embeddingResponse.data[0].embedding as number[]
}

export async function validateQdrantCollection(): Promise<void> {
  const client = new QdrantClient({
    url: `http://${process.env.QDRANT_HOST}:${process.env.QDRANT_PORT_HTTP}`,
  })

  try {
    await client.getCollection(COLLECTION_NAME)
  } catch (error: unknown) {
    await client.createCollection(COLLECTION_NAME, {
      vectors: {
        size: COLLECTION_WIDTH,
        distance: 'Cosine',
      },
    })
  }
}
