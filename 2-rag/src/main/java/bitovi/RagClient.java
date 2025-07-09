package bitovi;

import bitovi.common.TemporalClient;
import bitovi.common.Config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

public class RagClient {

	public static void main(String[] args) {
		Config config = new Config();
		String userId = config.getProperty("USER_ID");
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = "retrieval-augmented-generation-" + uuid + "-" + userId;

		WorkflowOptions workflowOptions = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId(workflowId)
				.build();

		RagWorkflow workflow = temporalClient
				.newWorkflowStub(RagWorkflow.class, workflowOptions);

		String response = workflow.execute();

		System.out.println("Workflow Executed: " + response);
	}
}
