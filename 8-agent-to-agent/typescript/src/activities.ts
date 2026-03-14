/**
 * Exercise 8 - Agent to Agent (TypeScript)
 *
 * TODO: This is a placeholder. The Java version of this exercise is fully
 * implemented. See the java/ directory for the complete implementation.
 *
 * The activities needed for the ReAct agent workflow include:
 *   - thoughtActivity: LLM decides next action or final answer
 *   - actionActivity: execute a tool (including A2A calls)
 *   - observationActivity: distill tool results into context
 *   - compactActivity: summarize context when it grows too large
 *   - persistActivity: save messages for history
 */

import dotenv from 'dotenv'
dotenv.config()

export async function thoughtActivity(): Promise<string> {
  throw new Error('Not Implemented — see java/ for reference implementation')
}

export async function actionActivity(): Promise<string> {
  throw new Error('Not Implemented — see java/ for reference implementation')
}

export async function observationActivity(): Promise<string> {
  throw new Error('Not Implemented — see java/ for reference implementation')
}
