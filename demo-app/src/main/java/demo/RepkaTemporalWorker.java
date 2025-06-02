package demo;

import demo.activities.CompletionsImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class RepkaTemporalWorker {
    
    public static void main(String[] args) {

        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("temporal:7233")
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker("default");

        // Register the workflow with the worker
        worker.registerWorkflowImplementationTypes(UserGreetingWorkflowImpl.class);

        // Register the activities with the worker
        worker.registerActivitiesImplementations(new CompletionsImpl());


        factory.start();
    }
}
