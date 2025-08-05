package bitovi;

import bitovi.common.TemporalClient;
import bitovi.common.Config;
import bitovi.activities.ActivitiesImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class AgentWorkflowWorker {
	public static void main(String[] args) {

		try {
			Config config = new Config();
			String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

			WorkflowClient temporalClient = TemporalClient.getTemporalClient();

			WorkerFactory factory = WorkerFactory.newInstance(temporalClient);
			Worker worker = factory.newWorker(taskQueue);

			worker.registerWorkflowImplementationTypes(AgentWorkflowImpl.class);
			worker.registerActivitiesImplementations(new ActivitiesImpl());

			factory.start();

			System.out.println("Exercise 5 Temporal Worker started. Press Ctrl+C to exit.");

			// Keep the worker running
			Thread.currentThread().join();
		} catch (Exception ex) {
			System.err.println("Failed to start Temporal Worker: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		}
	}
}
