package bitovi;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.workflow.AgentDecisionsPlanWorkflow;
import bitovi.workflow.AgentDecisionsReActWorkflow;
import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.PlanWorkflowResult;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;

public class AgentDecisionsClient {

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.out.println("Usage: java AgentDecisionsClient <agent-type>");
			System.out.println("  <agent-type>: 'reasoning-and-acting' or 'planning-and-executing'");
			return;
		}

		String agentType = args[0];

		Config config = new Config();
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = agentType + "-" + uuid;

		WorkflowOptions workflowOptions = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId(workflowId)
				.build();

		// Load Word Problem Text
		String wordProblem;
		// TODO_DECISIONS: Experiment with different word problem prompts
		try (InputStream wordProblemStream = AgentDecisionsClient.class.getClassLoader()
				.getResourceAsStream("word-problems/math-problem.txt")) {
			wordProblem = new String(wordProblemStream.readAllBytes()).trim();
		}

		// Send test message signal
		MessagePayload testMessage = new MessagePayload(
				"TestUser",
				wordProblem,
				LocalDateTime.now().toString());

		switch (agentType) {
			case "reasoning-and-acting": {
				String finalAnswerReceived = reasoningAndActingAgent(temporalClient, workflowId, workflowOptions,
						testMessage);
				System.out.println("\n\nFinal answer: " + finalAnswerReceived);
				break;
			}
			case "plan-and-execute": {
				String finalAnswerReceived = planningAndExecutingAgent(temporalClient, workflowId, workflowOptions,
						testMessage);
				System.out.println("\n\nFinal answer: " + finalAnswerReceived);
				break;
			}
			default:
				System.out.println("Unknown agent type: " + agentType);
		}
	}

	private static String reasoningAndActingAgent(WorkflowClient temporalClient, String workflowId,
			WorkflowOptions workflowOptions, MessagePayload testMessage) throws IOException, InterruptedException {
		AgentDecisionsReActWorkflow workflow = temporalClient
				.newWorkflowStub(AgentDecisionsReActWorkflow.class, workflowOptions);

		// Start workflow asynchronously with empty input
		WorkflowClient.start(workflow::execute, new WorkflowInput(null));

		System.out.println("Workflow started with ID: " + workflowId);

		workflow.receiveMessage(testMessage);

		System.out.println("Sent message signal");

		// Because the Workflow is designed to run forever and wait for signals
		// we can poll to see if a final result has been produced.
		String finalAnswerReceived = null;
		while (finalAnswerReceived == null) {
			Thread.sleep(1000);
			finalAnswerReceived = workflow.getAnswer();
		}

		// Send exit signal to end the workflow execution and get the final usage
		workflow.requestExit();

		System.out.println("Sent exit signal");

		// Get result
		WorkflowResult result = WorkflowStub.fromTyped(workflow).getResult(WorkflowResult.class);

		System.out.println("Workflow completed!");
		System.out.println("Usage metrics:");
		System.out.println("  Input tokens: " + result.usage().inputTokens());
		System.out.println("  Output tokens: " + result.usage().outputTokens());
		System.out.println("  Reasoning tokens: " + result.usage().reasoningTokens());
		System.out.println("  Total tokens: " + result.usage().totalTokens());

		// Return the final answer
		return finalAnswerReceived;
	}

	private static String planningAndExecutingAgent(WorkflowClient temporalClient, String workflowId,
			WorkflowOptions workflowOptions, MessagePayload testMessage) throws IOException, InterruptedException {
		AgentDecisionsPlanWorkflow workflow = temporalClient
				.newWorkflowStub(AgentDecisionsPlanWorkflow.class, workflowOptions);

		// Start workflow asynchronously with empty input
		WorkflowClient.start(workflow::execute, testMessage);

		System.out.println("Workflow started with ID: " + workflowId);

		System.out.println("Sent message signal");

		// Get result
		PlanWorkflowResult result = WorkflowStub.fromTyped(workflow).getResult(PlanWorkflowResult.class);

		System.out.println("Workflow completed!");
		System.out.println("Usage metrics:");
		System.out.println("  Input tokens: " + result.usage().inputTokens());
		System.out.println("  Output tokens: " + result.usage().outputTokens());
		System.out.println("  Reasoning tokens: " + result.usage().reasoningTokens());
		System.out.println("  Total tokens: " + result.usage().totalTokens());

		// Return the final answer
		return result.answer();
	}
}
