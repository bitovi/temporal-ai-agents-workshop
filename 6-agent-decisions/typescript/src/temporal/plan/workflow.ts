import { proxyActivities } from "@temporalio/workflow";
import type * as activities from "./activities";

const { Plan, ExecuteStep, PlanResponse } = proxyActivities<typeof activities>({
  startToCloseTimeout: "5 minutes",
  retry: {
    backoffCoefficient: 1,
    initialInterval: "3 seconds",
    maximumAttempts: 5,
  },
});

type PlanAgentWorkflowInput = {
  name: string;
  content: string;
  date: Date;
};

type PlanAgentWorkflowOutput = {
  result: string;
};

export async function PlanAgentWorkflow(
  input: PlanAgentWorkflowInput,
): Promise<PlanAgentWorkflowOutput> {
  const context: string[] = [
    `<user_message name="${input.name}" date="${input.date}">\n${input.content}\n</user_message>`,
  ];

  const plan = await Plan({ context });

  const steps = new Map<number, activities.PlanStep>();
  const result = new Map<number, string>();
  const failed: number[] = [];

  plan.steps.forEach((step) => {
    steps.set(step.id, step);
  });

  while (true) {
    // 1. Loop through the steps, find steps with no dependencies or completed dependencies.
    const pending: activities.PlanStep[] = [];

    plan.steps.forEach((step) => {
      // check if this step has dependencies
      if (
        step.dependsOn.length === 0 ||
        step.dependsOn.every((dep) => result.has(dep))
      ) {
        pending.push(step);
      }
    });

    if (pending.length === 0) {
      break;
    }

    // If every step has a result, we can break the loop and generate the final response
    if (Array.from(steps.keys()).every((id) => result.has(id))) {
      break;
    }

    // steps with no dependencies or completed dependencies can be executed in parallel
    await Promise.all(
      pending.map(async (step) => {
        const deps = step.dependsOn.map((dep) => ({
          id: dep,
          result: result.get(dep)!,
        }));

        try {
          const res = await ExecuteStep({ step, dependsOn: deps });
          result.set(step.id, res.result);
        } catch (error) {
          failed.push(step.id);
        }
      }),
    );

    if (failed.length > 0) {
      // TODO: Eventually we should re-plan based on the failed steps / existing state.
      return {
        result: "Unable to continue due to failed steps.",
      };
    }
  }

  // Combine the results of all steps into a single string
  const finalResult = await PlanResponse({
    steps: Array.from(steps.values()),
    results: Array.from(result.values()),
  });

  return finalResult;
}
