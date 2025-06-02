package demo;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.UUID;

public class RepkaTemporalClient {
    public static void main(String[] args) throws Exception {

        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("localhost:7233")
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

        WorkflowClient client = WorkflowClient.newInstance(service);

        String randomWorkflowId = "ollama-greetings-workflow-" + UUID.randomUUID().toString();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(randomWorkflowId)
                .setTaskQueue("default")
                .build();

        UserGreetingWorkflow workflow = client.newWorkflowStub(UserGreetingWorkflow.class, options);

        String greeting = workflow.greetSomeone("Mark");

        String workflowId = WorkflowStub.fromTyped(workflow).getExecution().getWorkflowId();

        System.out.println(workflowId + " " + greeting);

        service.shutdown();
    }
}
