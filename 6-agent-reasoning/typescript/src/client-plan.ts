import { randomUUID } from "node:crypto";
import dotenv from "dotenv";
import { Connection, Client } from "@temporalio/client";
import { PlanAgentWorkflow, ReactAgentWorkflow } from "./temporal/workflows";
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

  const question = `What is ((2025.3 + 378.8 ) / 37) * 5.7 equal to?`;

  try {
    const handle = await client.workflow.start(PlanAgentWorkflow, {
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
