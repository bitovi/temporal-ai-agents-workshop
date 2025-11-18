import { proxyActivities } from '@temporalio/workflow'
import type * as activities from './activities'

const { checkBedrockConnection } = proxyActivities<typeof activities>({
  startToCloseTimeout: '1 minute',
  retry: {
    backoffCoefficient: 1,
    initialInterval: '3 seconds',
    maximumAttempts: 3,
  },
})

export async function environmentSetupWorkflow(): Promise<string> {
  await checkBedrockConnection()
  return 'Success'
}
