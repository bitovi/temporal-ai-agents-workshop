package bitovi;

import java.time.LocalDateTime;

import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.workflow.AgentToAgentWorkflow;
import bitovi.workflow.types.MessagePayload;
import bitovi.workflow.types.WorkflowInput;
import bitovi.workflow.types.WorkflowResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;

public class AgentToAgentClient {

	public static void main(String[] args) throws Exception {
		Config config = new Config();
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = "agent-workflow-" + uuid;

		WorkflowOptions workflowOptions = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId(workflowId)
				.build();

		AgentToAgentWorkflow workflow = temporalClient
				.newWorkflowStub(AgentToAgentWorkflow.class, workflowOptions);

		// Start workflow asynchronously with empty input
		WorkflowClient.start(workflow::execute, new WorkflowInput(null));

		System.out.println("Workflow started with ID: " + workflowId);

		// Send test message signal
		MessagePayload testMessage = new MessagePayload(
				"TestUser",
				// TODO_A2A: Experiment with different questions
				"What purchases have I made from Riot recently?",
				LocalDateTime.now().toString());
		workflow.receiveMessage(testMessage);

		System.out.println("Sent message signal");

		// Because the Workflow is designed to run forever and wait for signals
		// we can poll to see if a final result has been produced.
		String finalAnswerReceived = null;
		while (finalAnswerReceived == null) {
			Thread.sleep(1000);
			finalAnswerReceived = workflow.getAnswer();
		}

		// Send exit signal
		workflow.requestExit();

		System.out.println("Sent exit signal");

		// Get result
		WorkflowResult result = WorkflowStub.fromTyped(workflow).getResult(WorkflowResult.class);

		System.out.println("Workflow completed!");
		System.out.println("Usage metrics:");
		System.out.println("  Input tokens: " + result.usage().inputTokens());
		System.out.println("  Output tokens: " + result.usage().outputTokens());
		System.out.println("  Total tokens: " + result.usage().totalTokens());

		// Return the final answer
		System.out.println("Final answer received from workflow: " + finalAnswerReceived);
	}
}
