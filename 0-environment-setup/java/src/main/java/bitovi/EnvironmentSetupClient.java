package bitovi;

import java.io.FileNotFoundException;

import javax.net.ssl.SSLException;

import bitovi.common.Config;
import bitovi.common.TemporalClient;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

public class EnvironmentSetupClient {

	public static void main(String[] args) throws SSLException, FileNotFoundException {
		Config config = new Config();
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = "environment-setup-workflow-" + uuid;

		System.out.println("Starting Environment Setup Workflow with ID: " + workflowId);
		WorkflowOptions workflowOptions = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId(workflowId)
				.build();

		EnvironmentSetupWorkflow workflow = temporalClient
				.newWorkflowStub(EnvironmentSetupWorkflow.class, workflowOptions);

		String response = workflow.execute();

		System.out.println("Workflow Executed: " + response);
	}
}
