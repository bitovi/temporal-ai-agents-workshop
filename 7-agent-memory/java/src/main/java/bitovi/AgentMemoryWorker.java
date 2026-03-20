package bitovi;

import bitovi.activities.ActivitiesImpl;
import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.workflow.AgentMemoryWorkflowImpl;
import bitovi.workflow.MemoryExtractionWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class AgentMemoryWorker {
	public static void main(String[] args) {

		try {
			Config config = new Config();
			String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

			WorkflowClient temporalClient = TemporalClient.getTemporalClient();

			WorkerFactory factory = WorkerFactory.newInstance(temporalClient);
			Worker worker = factory.newWorker(taskQueue);

			worker.registerWorkflowImplementationTypes(AgentMemoryWorkflowImpl.class);
			worker.registerWorkflowImplementationTypes(MemoryExtractionWorkflowImpl.class);
			worker.registerActivitiesImplementations(new ActivitiesImpl());

			factory.start();

			System.out.println("Exercise 7 Temporal Worker started. Press Ctrl+C to exit.");

			// Keep the worker running
			Thread.currentThread().join();
		} catch (Exception ex) {
			System.err.println("Failed to start Temporal Worker: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		}
	}
}
