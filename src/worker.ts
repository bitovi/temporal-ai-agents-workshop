import dotenv from 'dotenv'
import { NativeConnection, Worker } from '@temporalio/worker'
import * as activities from './activities'
import { getTemporalClientOptions } from './utils'

dotenv.config()

export async function createWorker() {
  const temporalClientOptions = getTemporalClientOptions()
  const connection = await NativeConnection.connect(temporalClientOptions)

  const worker = await Worker.create({
    connection,
    namespace: process.env.TEMPORAL_NAMESPACE,
    taskQueue: process.env.TEMPORAL_TASK_QUEUE || 'agent-queue',
    workflowsPath: require.resolve('./workflows'),
    activities,
  })

  return worker
}

export async function startWorker() {
  console.log('🔧 Initializing Temporal worker...')

  const worker = await createWorker()

  console.log('⚡ Temporal worker started successfully')
  console.log(`📋 Task queue: ai-financial-tueues-queue`)
  console.log(`🌐 Namespace: ${process.env.TEMPORAL_NAMESPACE || 'default'}`)

  // Start the worker (this will run indefinitely)
  await worker.run()
}

// If this file is run directly, start the worker
if (require.main === module) {
  async function main() {
    try {
      console.log('🚀 Starting Temporal worker...')
      await startWorker()
    } catch (error) {
      console.error('❌ Failed to start worker:', error)
      process.exit(1)
    }
  }

  // Handle graceful shutdown
  process.on('SIGINT', () => {
    console.log('\n🛑 Received SIGINT, shutting down worker gracefully...')
    process.exit(0)
  })

  process.on('SIGTERM', () => {
    console.log('\n🛑 Received SIGTERM, shutting down worker gracefully...')
    process.exit(0)
  })

  // Start the worker
  main().catch((error) => {
    console.error('💥 Unexpected error starting worker:', error)
    process.exit(1)
  })
}
