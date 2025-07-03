package bitovi;

import java.util.concurrent.ExecutionException;

import bitovi.workflows.AgentGoal.AgentGoalWorkflowImpl;
import bitovi.workflows.AgentGoal.activities.AgentGoalsActivitiesImpl;
import bitovi.workflows.Chat.ChatWorkflowImpl;
import bitovi.workflows.Chat.activities.ChatActivitiesImpl;
import bitovi.workflows.DocumentIngest.DocumentIngestImpl;
import bitovi.workflows.DocumentIngest.activities.DocumentIngestActivitiesImpl;
import bitovi.workflows.UserGreeting.UserGreetingWorkflowImpl;
import bitovi.workflows.UserGreeting.activities.CompletionsImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class BitoviCommonTemporalWorker {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("temporal:7233")
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker("default");

        // Register the workflow with the worker
        worker.registerWorkflowImplementationTypes(UserGreetingWorkflowImpl.class);
        worker.registerWorkflowImplementationTypes(AgentGoalWorkflowImpl.class);
        worker.registerWorkflowImplementationTypes(ChatWorkflowImpl.class);
        worker.registerWorkflowImplementationTypes(DocumentIngestImpl.class);

        // Register the activities with the worker for completions
        worker.registerActivitiesImplementations(new CompletionsImpl());

        // Register the activities with the worker for agent goals
        worker.registerActivitiesImplementations(new AgentGoalsActivitiesImpl());

        // Register the activities with the worker for chat
        worker.registerActivitiesImplementations(new ChatActivitiesImpl());

        // Register the activities with the worker for document ingest
        worker.registerActivitiesImplementations(new DocumentIngestActivitiesImpl());

        factory.start();

        // Keep the worker running
    }
}
