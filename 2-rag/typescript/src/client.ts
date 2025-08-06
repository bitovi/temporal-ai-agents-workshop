import dotenv from 'dotenv'
import { Connection, Client } from '@temporalio/client'
import { v4 as uuidv4 } from 'uuid'
import { getTemporalClientOptions } from './utils'
import { retrievalAugmentedGenerationWorkflow, documentEmbeddingWorkflow } from './workflows'

dotenv.config()

async function main() {
  const connection = await Connection.connect(getTemporalClientOptions())

  const client = new Client({
    connection,
    namespace: process.env.TEMPORAL_NAMESPACE,
  })

  documentEmbedding(client)
  retrievalAugmentedGeneration(client, 'How do I change my account id?')
}

async function retrievalAugmentedGeneration(client: Client, query: string) {
  try {
    const handle = await client.workflow.start(retrievalAugmentedGenerationWorkflow, {
      args: [
        {
          query: query,
        },
      ],
      taskQueue: process.env.TEMPORAL_TASK_QUEUE || 'agent-queue',
      workflowId: `retrieval-augmented-generation-${uuidv4()}`,
    })

    console.log('Workflow started with ID: %s', handle.workflowId)

    const result: string = await handle.result()

    console.log(`Response: ${result}`)
  } catch (error: any) {
    console.error('Error executing workflow:', error)
    process.exit(1)
  }
}

async function documentEmbedding(client: Client) {
  try {
    const handle = await client.workflow.start(documentEmbeddingWorkflow, {
      args: [
        {
          urls: [
            'policies/Account-Deactivation-and-Deletion.txt',
            'policies/Account-Transfer.txt',
            'policies/Changing-Your-Riot-ID.txt',
            'policies/Protecting-Your-Account.txt',
            'policies/Requesting-Your-Account-Data.txt',
          ],
        },
      ],
      taskQueue: process.env.TEMPORAL_TASK_QUEUE || 'agent-queue',
      workflowId: `document-embedding-${uuidv4()}`,
    })

    console.log('Workflow started with ID: %s', handle.workflowId)

    await handle.result()
    console.log(`Response: Document embedding completed successfully`)
  } catch (error: any) {
    console.error('Error executing workflow:', error)
    process.exit(1)
  }
}

main().catch((error) => {
  console.error('Unexpected error:', error)
  process.exit(1)
})
