import { randomUUID } from "node:crypto";
import dotenv from "dotenv";
import { Connection, Client } from "@temporalio/client";
import { ReactAgentWorkflow } from "./temporal/workflows";
import { Config } from "./config";

dotenv.config();

async function main() {
  const connection = await Connection.connect(Config.TEMPORAL_CLIENT_OPTIONS);

  const client = new Client({
    connection,
    namespace: Config.TEMPORAL_NAMESPACE,
  });

  const workflowOptions = {
    taskQueue: Config.TEMPORAL_TASK_QUEUE,
    workflowId: `agent-${randomUUID()}`,
  };

  const question = `A  13  foot ladder leans against a wall.  The foot of a ladder begins to slide away from the wall
at the rate of 1 foot per minute.  When the foot is  5 ft from the wall,  at what rate is the top of the ladder is falling?`;

  try {
    const handle = await client.workflow.start(ReactAgentWorkflow, {
      args: [
        {
          name: "User",
          date: new Date(),
          content: question,
        },
      ],
      ...workflowOptions,
    });

    console.log("Workflow started with ID: %s", handle.workflowId);

    const result = await handle.result();
    console.log("Workflow input:", question);
    console.log("Workflow result:", result.result);
  } catch (error: any) {
    console.error("Error executing workflow:", error);
    process.exit(1);
  }
}

main().catch((error) => {
  console.error("Unexpected error:", error);
  process.exit(1);
});
