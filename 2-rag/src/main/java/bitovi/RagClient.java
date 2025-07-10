package bitovi;

import bitovi.common.TemporalClient;

import java.io.File;

import bitovi.common.AWS;
import bitovi.common.Config;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;

public class RagClient {

	public static void main(String[] args) {

		if (args.length < 1) {
			System.out.println("Please provide the path to the documents.");
			return;
		}

		String pathToDocuments = args[0];
		String[] urls = uploadDocuments(pathToDocuments);

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

		RagClient.importDocumentsWorkflow(temporalClient, workflowOptions, urls);
		RagClient.searchDocumentsWorkflow(temporalClient, workflowOptions);
	}

	public static String[] uploadDocuments(String pathToDocuments) {
		// If the path ends with a slash, remove it
		if (pathToDocuments.endsWith(File.separator)) {
			pathToDocuments = pathToDocuments.substring(0, pathToDocuments.length() - 1);
		}

		// Get a list of all the .txt files in `pathToDocuments`
		File dir = new File(pathToDocuments);
		if (!dir.exists() || !dir.isDirectory()) {
			return new String[0];
		}

		String[] files = dir.list((d, name) -> name.endsWith(".txt"));
		// for each file, upload to S3 and return the S3 URL
		if (files == null || files.length == 0) {
			System.out.println("No .txt files found in the specified directory.");
			return new String[0];
		}

		String[] urls = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			String filePath = pathToDocuments + File.separator + files[i];
			String bucketName = new Config().getProperty("AWS_S3_BUCKET_NAME");
			String key = files[i] + "-" + System.currentTimeMillis(); // Use the file name as the key
			String s3Url = AWS.uploadFile(bucketName, key, filePath);
			urls[i] = s3Url;
		}

		return urls;
	}

	public static void importDocumentsWorkflow(WorkflowClient temporalClient, WorkflowOptions workflowOptions,
			String[] urls) {
		EmbedWorkflow workflow = temporalClient
				.newWorkflowStub(EmbedWorkflow.class, workflowOptions);

		workflow.execute(urls);

		System.out.println("Embed Workflow Finished.");
	}

	public static void searchDocumentsWorkflow(WorkflowClient temporalClient, WorkflowOptions workflowOptions) {
		RagWorkflow workflow = temporalClient
				.newWorkflowStub(RagWorkflow.class, workflowOptions);

		String response = workflow.execute("How do I delete my Riot account?");

		System.out.println("Search Workflow Executed: " + response);
	}
}
