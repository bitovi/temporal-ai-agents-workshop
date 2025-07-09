package bitovi;

import bitovi.common.TemporalClient;
import bitovi.common.Config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

public class PromptEngineeringClient {

	public static void main(String[] args) {
		Config config = new Config();
		String userId = config.getProperty("USER_ID");
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = "prompt-engineering-workflow-" + uuid + "-" + userId;

		WorkflowOptions workflowOptions = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId(workflowId)
				.build();

		PromptEngineeringWorkflow workflow = temporalClient
				.newWorkflowStub(PromptEngineeringWorkflow.class, workflowOptions);

		String response = workflow.execute();

		System.out.println("Workflow Executed: " + response);
	}
}
