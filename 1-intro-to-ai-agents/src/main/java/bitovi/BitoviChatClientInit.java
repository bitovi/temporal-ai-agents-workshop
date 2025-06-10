package bitovi;

import java.util.Scanner;

import bitovi.workflows.Chat.ChatWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public class BitoviChatClientInit {
    public static void main(String[] args) throws Exception {

        // Prompt the user for a chat message from stdin
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your User Id:");
        String userId = scanner.nextLine();
        if (userId == null || userId.trim().isEmpty()) {
            System.out.println("No User Id provided. Exiting.");
            scanner.close();
            return; // Exit if no user ID is provided
        }
        scanner.close();

        String workflowId = "chat-workflow-" + userId;

        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("localhost:7233")
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

        WorkflowClient client = WorkflowClient.newInstance(service);

        WorkflowOptions workflowOptions = WorkflowOptions.newBuilder().setTaskQueue("default").setWorkflowId(workflowId)
                .build();

        // Create the workflow client stub. It is used to start the workflow execution.
        ChatWorkflow workflow = client.newWorkflowStub(ChatWorkflow.class, workflowOptions);

        // Start workflow asynchronously
        WorkflowClient.start(workflow::run);

        System.out.println(workflowId + " workflow started.");

        System.exit(0);
    }
}
