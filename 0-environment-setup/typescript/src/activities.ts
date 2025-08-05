import dotenv from 'dotenv'
import postgres from 'postgres'
import { HeadBucketCommand, S3Client } from '@aws-sdk/client-s3'
import { BedrockRuntimeClient, ConverseCommand } from '@aws-sdk/client-bedrock-runtime'
import { QdrantClient } from '@qdrant/js-client-rest'
import { OpenAI as OpenAIClient } from 'openai'

dotenv.config()

export async function checkPostgresConnection(): Promise<void> {
  const sql = postgres({
    host: process.env.POSTGRES_HOST,
    port: Number.parseInt(process.env.POSTGRES_PORT ?? '5432'),
    database: process.env.POSTGRES_DATABASE,
    username: process.env.POSTGRES_USERNAME,
    password: process.env.POSTGRES_PASSWORD,
  })

  await sql`SELECT 1`
}

export async function checkQdrantConnection(): Promise<void> {
  const client = new QdrantClient({
    host: process.env.QDRANT_HOST,
    port: Number.parseInt(process.env.QDRANT_PORT ?? '6333'),
  })

  await client.getCollections()
}

export async function checkS3Connection(): Promise<void> {
  const client = new S3Client({
    region: process.env.AWS_REGION as string,
    credentials: {
      accessKeyId: process.env.AWS_ACCESS_KEY_ID || '',
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || '',
      sessionToken: process.env.AWS_SESSION_TOKEN || undefined,
    },
  })

  await client.send(new HeadBucketCommand({ Bucket: process.env.AWS_S3_BUCKET_NAME as string }))
}

export async function checkBedrockConnection(): Promise<void> {
  const client = new BedrockRuntimeClient({
    region: process.env.AWS_REGION as string,
    credentials: {
      accessKeyId: process.env.AWS_ACCESS_KEY_ID || '',
      secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY || '',
      sessionToken: process.env.AWS_SESSION_TOKEN || undefined,
    },
  })

  await client.send(
    new ConverseCommand({
      modelId: process.env.AWS_MODEL_ID as string,
      system: [
        {
          text: "You are a simple test model that only responds with 'yes' or 'no'.",
        },
      ],
      messages: [
        {
          role: 'user',
          content: [
            {
              text: 'Can you read this message?',
            },
          ],
        },
      ],
    })
  )
}

export async function checkOpenAIConnection(): Promise<void> {
  const client = new OpenAIClient({
    apiKey: process.env.OPENAI_API_KEY,
    baseURL: process.env.OPENAI_API_BASE,
  })

  await client.chat.completions.create({
    model: process.env.OPENAI_MODEL as string,
    messages: [
      {
        role: 'system',
        content: 'You are a simple test model that only responds with "yes" or "no".',
      },
      {
        role: 'user',
        content: 'Can you read this message?',
      },
    ],
  })
}
