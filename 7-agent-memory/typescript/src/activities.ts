import { randomUUID } from 'node:crypto'
import dotenv from 'dotenv'
import { createBedrockEvent, listMemoryRecordsCommand } from './utils'
dotenv.config()

const createMemory = false

export async function checkBedrockConnection(): Promise<void> {
  const actorId = process.env.AWS_BEDROCK_ACTOR_ID!
  console.log('Using Actor ID:', actorId)

  const sessionId = process.env.AWS_BEDROCK_SESSION_ID!
  console.log('Using Session ID:', sessionId)

  if (createMemory) {
    const response = await createBedrockEvent({
      actorId: actorId,
      eventTimestamp: new Date(),
      // This should be created in AWS Bedrock Console beforehand. This ID can be found
      // here https://us-east-1.console.aws.amazon.com/bedrock-agentcore/memory?region=us-east-1
      memoryId: process.env.AWS_BEDROCK_MEMORY_ID!,
      payload: [
        {
          conversational: {
            content: { text: 'I like sushi with tuna' },
            role: 'USER',
          },
        },
        {
          conversational: {
            content: { text: 'That sounds delicious! Tuna sushi is a great choice.' },
            role: 'ASSISTANT',
          },
        },
        {
          conversational: {
            content: { text: 'I also like pizza' },
            role: 'USER',
          },
        },
        {
          conversational: {
            content: { text: 'Pizza is another excellent choice! You have great taste in food.' },
            role: 'ASSISTANT',
          },
        },
      ],
      sessionId: sessionId,
      metadata: {
        key: {
          stringValue: 'value',
        },
      },
      clientToken: randomUUID(),
    })

    console.log('Bedrock Event Created:', response.event)
  }

  const userFacts = await listMemoryRecordsCommand(`/users/${actorId}/facts`)
  console.log('Listed Memory Records:', userFacts.memoryRecordSummaries)

  const userPreferences = await listMemoryRecordsCommand(`/users/${actorId}/preferences`)
  console.log('Listed Memory Records:', userPreferences.memoryRecordSummaries)

  const sessionSummaries = await listMemoryRecordsCommand(`/summaries/${actorId}/${sessionId}`)
  console.log('Listed Memory Records:', sessionSummaries.memoryRecordSummaries)
}
