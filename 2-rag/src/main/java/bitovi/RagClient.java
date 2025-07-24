package bitovi;

import bitovi.common.TemporalClient;

import bitovi.common.Config;
import bitovi.common.Setup;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

public class RagClient {

	/**
	 * Main method to start the RagClient and execute the workflows.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		// By default this is the `documents` folder in this exercise
		// "${workspaceFolder}/2-rag/documents/"
		if (args.length < 1) {
			System.out.println("Please provide the path to the documents.");
			return;
		}

		String pathToDocuments = args[0];
		String[] urls = Setup.uploadDocuments(pathToDocuments);

		Config config = new Config();
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();
		String workflowId = "retrieval-augmented-generation-" + uuid;

		WorkflowOptions workflowOptions = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId(workflowId)
				.build();

		// Start off by importing the documents into the vector database
		EmbedWorkflow embedWorkflow = temporalClient
				.newWorkflowStub(EmbedWorkflow.class, workflowOptions);

		embedWorkflow.execute(urls);

		System.out.println("Embed Workflow Finished.");

		// Search for documents and build a prompt based on the search query
		// TODO_RAG: Enter a search query (e.g., "How do I change my Riot ID?")

		RagWorkflow searchWorkflow = temporalClient
				.newWorkflowStub(RagWorkflow.class, workflowOptions);

		String response = searchWorkflow.execute("How do I change my Riot ID?");

		System.out.println("Search Workflow Executed: " + response);
	}
}
