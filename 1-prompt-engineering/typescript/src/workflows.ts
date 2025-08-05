import { proxyActivities } from '@temporalio/workflow'
import type * as activities from './activities'

const {
  checkBedrockConnection,
  checkQdrantConnection,
  checkS3Connection,
  checkOpenAIConnection,
  checkPostgresConnection,
} = proxyActivities<typeof activities>({
  startToCloseTimeout: '1 minute',
  retry: {
    backoffCoefficient: 1,
    initialInterval: '3 seconds',
    maximumAttempts: 3,
  },
})

export async function environmentSetupWorkflow(): Promise<string> {
  await checkBedrockConnection()
  await checkOpenAIConnection()
  await checkPostgresConnection()
  await checkQdrantConnection()
  await checkS3Connection()

  return 'Success'
}
