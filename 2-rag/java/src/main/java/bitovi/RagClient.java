package bitovi;

import bitovi.common.TemporalClient;

import bitovi.common.Config;
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
		// if (args.length < 1) {
		// System.out.println("Please provide the path to the documents.");
		// return;
		// }

		// String pathToDocuments = args[0];
		// String[] urls = Setup.uploadDocuments(pathToDocuments);
		String[] urls = {
				"policies/Account-Deactivation-and-Deletion.txt",
				"policies/Account-Transfer.txt",
				"policies/Changing-Your-Riot-ID.txt",
				"policies/Protecting-Your-Account.txt",
				"policies/Requesting-Your-Account-Data.txt"
		};
		Config config = new Config();
		String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

		WorkflowClient temporalClient = TemporalClient.getTemporalClient();

		String uuid = java.util.UUID.randomUUID().toString();

		WorkflowOptions workflowOptionsDocuments = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId("document-embedding-" + uuid)
				.build();

		// Start off by importing the documents into the vector database
		EmbedWorkflow embedWorkflow = temporalClient
				.newWorkflowStub(EmbedWorkflow.class, workflowOptionsDocuments);

		embedWorkflow.execute(urls);

		System.out.println("Embed Workflow Finished.");

		// Search for documents and build a prompt based on the search query
		// TODO_RAG: Enter a search query (e.g., "How do I change my Riot ID?")

		WorkflowOptions workflowOptionsRag = WorkflowOptions
				.newBuilder()
				.setTaskQueue(taskQueue)
				.setWorkflowId("retrieval-augmented-generation-" + uuid)
				.build();

		RagWorkflow searchWorkflow = temporalClient
				.newWorkflowStub(RagWorkflow.class, workflowOptionsRag);

		String response = searchWorkflow.execute("How do I change my Riot ID?");

		System.out.println("Search Workflow Executed: " + response);
	}
}
