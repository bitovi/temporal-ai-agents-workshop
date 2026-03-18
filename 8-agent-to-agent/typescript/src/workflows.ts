/**
 * Exercise 8 - Agent to Agent (TypeScript)
 *
 * TODO: This is a placeholder. The Java version of this exercise is fully
 * implemented. See the java/ directory for the complete implementation.
 *
 * The workflow should implement a ReAct loop that:
 *   1. Waits for user messages via signals
 *   2. Runs thought → action → observation cycles
 *   3. Supports A2A multi-turn conversations (input-required → follow-up)
 *   4. Returns a final answer when the LLM is confident
 */

import { proxyActivities } from '@temporalio/workflow'
import type * as activities from './activities'

const { thoughtActivity, actionActivity, observationActivity } = proxyActivities<typeof activities>(
  {
    startToCloseTimeout: '2 minutes',
    retry: {
      backoffCoefficient: 1,
      initialInterval: '3 seconds',
      maximumAttempts: 3,
    },
  }
)

export async function agentWorkflow(): Promise<string> {
  throw new Error('Not Implemented — see java/ for reference implementation')
}
