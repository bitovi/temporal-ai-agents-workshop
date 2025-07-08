package bitovi;

import bitovi.common.TemporalClient;
import bitovi.activities.BedrockImpl;
import bitovi.activities.ZendeskImpl;
import bitovi.activities.PostgresImpl;
import bitovi.activities.QdrantImpl;
import bitovi.common.Config;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class EnvironmentSetupWorker {
	public static void main(String[] args) {

		try {
			Config config = new Config();
			String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

			WorkflowClient temporalClient = TemporalClient.getTemporalClient();

			WorkerFactory factory = WorkerFactory.newInstance(temporalClient);
			Worker worker = factory.newWorker(taskQueue);

			worker.registerWorkflowImplementationTypes(EnvironmentSetupWorkflowImpl.class);
			worker.registerActivitiesImplementations(new ZendeskImpl());
			worker.registerActivitiesImplementations(new BedrockImpl());
			worker.registerActivitiesImplementations(new PostgresImpl());
			worker.registerActivitiesImplementations(new QdrantImpl());

			factory.start();

			System.out.println("Temporal Worker started. Press Ctrl+C to exit.");

			// Keep the worker running
			Thread.currentThread().join();
		} catch (Exception ex) {
			System.err.println("Failed to start Temporal Worker: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		}
	}
}
