package bitovi;

import bitovi.common.Config;
import bitovi.common.TemporalClient;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

public class PromptEngineeringClient {

	public static void main(String[] args) {
		Config config = new Config();
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = "prompt-engineering-workflow-" + uuid;

		WorkflowOptions workflowOptions = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId(workflowId)
				.build();

		PromptEngineeringWorkflow workflow = temporalClient
				.newWorkflowStub(PromptEngineeringWorkflow.class, workflowOptions);

		String response = workflow.execute(
				"7:44 AM: How do I reset my password?",
				"7:51 AM: To reset your password, go to the login page and click on 'Forgot Password?'. Follow the instructions to reset your password via email or SMS.");

		System.out.println("Workflow Executed: " + response);
	}
}
