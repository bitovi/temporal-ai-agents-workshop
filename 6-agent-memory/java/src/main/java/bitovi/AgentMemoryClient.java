package bitovi;

import bitovi.common.TemporalClient;

import java.io.FileNotFoundException;

import javax.net.ssl.SSLException;

import bitovi.common.Config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

public class AgentMemoryClient {

	public static void main(String[] args) throws SSLException, FileNotFoundException {
		Config config = new Config();
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = "agent-memory-workflow-" + uuid;

		WorkflowOptions workflowOptions = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId(workflowId)
				.build();

		AgentMemoryWorkflow workflow = temporalClient
				.newWorkflowStub(AgentMemoryWorkflow.class, workflowOptions);

		String response = workflow.execute();

		System.out.println("Workflow Executed: " + response);
	}
}
