package bitovi;

import java.util.Scanner;

import bitovi.workflows.UserGreeting.UserGreetingWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public class BitoviGreetingWorkflowInit {
    public static void main(String[] args) throws Exception {

        // Prompt the user for a chat message from stdin
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your name:");
        String userId = scanner.nextLine();
        if (userId == null || userId.trim().isEmpty()) {
            System.out.println("No name provided. Exiting.");
            scanner.close();
            return; // Exit if no name is provided
        }
        scanner.close();

        // generate a random uuid for the workflow ID suffix

        String uuid = java.util.UUID.randomUUID().toString();

        String workflowId = "greeting-workflow-" + uuid + "-" + userId;
        System.out.println("Starting workflow with ID: " + workflowId);

        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("localhost:7233")
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

        WorkflowClient client = WorkflowClient.newInstance(service);

        WorkflowOptions workflowOptions = WorkflowOptions.newBuilder().setTaskQueue("default").setWorkflowId(workflowId)
                .build();

        // Create the workflow client stub. It is used to start the workflow execution.
        UserGreetingWorkflow workflow = client.newWorkflowStub(UserGreetingWorkflow.class, workflowOptions);

        String greeting = workflow.greetSomeone(userId);

        System.out.println("Greeting received: " + greeting);
        System.exit(0);
    }
}
