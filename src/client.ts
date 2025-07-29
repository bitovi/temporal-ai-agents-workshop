import dotenv from 'dotenv';
import { Connection, Client } from '@temporalio/client';
import { v4 as uuidv4 } from 'uuid';
import { getTemporalClientOptions } from './utils';
import { agentWorkflow } from './workflows';

dotenv.config();

async function main() {
  const connection = await Connection.connect(getTemporalClientOptions());

  const client = new Client({
    connection,
    namespace: process.env.TEMPORAL_NAMESPACE,
  });

  const workflowId = `${uuidv4()}`;

  const workflowOptions = {
    taskQueue: process.env.TEMPORAL_TASK_QUEUE || 'agent-queue',
    workflowId: workflowId,
  };

  try {
    const handle = await client.workflow.start(agentWorkflow, {
      args: [],
      ...workflowOptions,
    });

    console.log('Workflow started with ID: %s', handle.workflowId);

    const result: string = await handle.result();

    console.log(`Response: ${result}`);
  } catch (error: any) {
    console.error('Error executing workflow:', error);
    process.exit(1);
  }
}

main().catch((error) => {
  console.error('Unexpected error:', error);
  process.exit(1);
});