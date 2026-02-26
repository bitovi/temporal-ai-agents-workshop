import dotenv from 'dotenv'
dotenv.config()

export async function checkPostgresConnection(): Promise<void> {
  throw new Error('Not Implemented')
}

export async function checkQdrantConnection(): Promise<void> {
  throw new Error('Not Implemented')
}

export async function checkS3Connection(): Promise<void> {
  throw new Error('Not Implemented')
}

export async function checkBedrockConnection(): Promise<void> {
  throw new Error('Not Implemented')
}

export async function checkOpenAIConnection(): Promise<void> {
  throw new Error('Not Implemented')
}
