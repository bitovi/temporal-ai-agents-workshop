import { proxyActivities } from "@temporalio/workflow";
import type * as activities from "./activities";

const { ReactThought, ReactAction, ReactObservation } = proxyActivities<
  typeof activities
>({
  startToCloseTimeout: "5 minutes",
  retry: {
    backoffCoefficient: 1,
    initialInterval: "3 seconds",
    maximumAttempts: 5,
  },
});

type ReactAgentWorkflowInput = {
  name: string;
  content: string;
  date: Date;
};

type ReactAgentWorkflowOutput = {
  result: string;
};

export async function ReactAgentWorkflow(
  input: ReactAgentWorkflowInput,
): Promise<ReactAgentWorkflowOutput> {
  const context: string[] = [
    `<user_message name="${input.name}" date="${input.date}">\n${input.content}\n</user_message>`,
  ];

  while (true) {
    const thought = await ReactThought({ context });
    if (thought.answer) {
      return { result: thought.answer };
    }

    if (thought.action) {
      context.push(`<thought>\n${thought.reasoning}\n</thought>`);

      const action = await ReactAction({
        name: thought.action.name,
        input: thought.action.input,
      });

      if (action.error) {
        context.push(
          `<action><name>${thought.action.name}</name><input>${JSON.stringify(thought.action.input)}</input><error>${action.error}</error></action>`,
        );
      }

      if (!action.error) {
        const observation = await ReactObservation({
          reasoning: thought.reasoning,
          action: thought.action,
          result: action.result,
        });

        context.push(
          `<action><name>${thought.action.name}</name><input>${JSON.stringify(thought.action.input)}</input></action>`,
        );
        context.push(`<observation>\n${observation.result}\n</observation>`);
      }
    }
  }
}
