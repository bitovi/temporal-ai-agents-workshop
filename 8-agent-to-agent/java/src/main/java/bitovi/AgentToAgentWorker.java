package bitovi;

import bitovi.activities.ActivitiesImpl;
import bitovi.common.Config;
import bitovi.common.TemporalClient;
import bitovi.workflow.AgentToAgentWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

/**
 * Temporal Worker that hosts the Agent-to-Agent workflow and activities.
 *
 * This registers:
 * - AgentToAgentWorkflowImpl — the ReAct loop workflow
 * - ActivitiesImpl — thought, action, observation, compact, persist activities
 *
 * The worker listens on the configured task queue and executes workflow/activity
 * tasks dispatched by the Temporal server.
 */
public class AgentToAgentWorker {
	public static void main(String[] args) {

		try {
			Config config = new Config();
			String taskQueue = config.getProperty("TEMPORAL_TASK_QUEUE");

			WorkflowClient temporalClient = TemporalClient.getTemporalClient();

			WorkerFactory factory = WorkerFactory.newInstance(temporalClient);
			Worker worker = factory.newWorker(taskQueue);

			worker.registerWorkflowImplementationTypes(AgentToAgentWorkflowImpl.class);
			worker.registerActivitiesImplementations(new ActivitiesImpl());

			factory.start();

			System.out.println("Exercise 8 Temporal Worker started. Press Ctrl+C to exit.");

			// Keep the worker running
			Thread.currentThread().join();
		} catch (Exception ex) {
			System.err.println("Failed to start Temporal Worker: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		}
	}
}
