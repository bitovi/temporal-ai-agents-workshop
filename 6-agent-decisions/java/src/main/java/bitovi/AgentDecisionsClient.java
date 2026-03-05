package bitovi;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.workflow.AgentDecisionsWorkflow;
import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;

public class AgentDecisionsClient {

	public static void main(String[] args) throws Exception {
		Config config = new Config();
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");
		String agentType = config.getProperty("AGENT_TYPE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = "agent-workflow-" + uuid;

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

		if (agentType.equals("reasoning-and-acting")) {
			String finalAnswerReceived = reasoningAndActingAgent(temporalClient, workflowId, workflowOptions,
					testMessage);
			System.out.println("\n\nFinal answer: " + finalAnswerReceived);
		} else if (agentType.equals("planning-and-executing")) {
			String finalAnswerReceived = planningAndExecutingAgent(temporalClient, workflowId, workflowOptions,
					testMessage);
			System.out.println("\n\nFinal answer: " + finalAnswerReceived);
		} else {
			System.out.println("Unknown agent type: " + agentType);
			return;
		}
	}

	private static String reasoningAndActingAgent(WorkflowClient temporalClient, String workflowId,
			WorkflowOptions workflowOptions, MessagePayload testMessage) throws IOException, InterruptedException {
		AgentDecisionsWorkflow workflow = temporalClient
				.newWorkflowStub(AgentDecisionsWorkflow.class, workflowOptions);

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
		AgentDecisionsWorkflow workflow = temporalClient
				.newWorkflowStub(AgentDecisionsWorkflow.class, workflowOptions);

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
}
