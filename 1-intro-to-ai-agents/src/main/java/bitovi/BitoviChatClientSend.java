package bitovi;

import java.io.IOException;
import java.util.Scanner;

import bitovi.workflows.Chat.ChatWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

public class BitoviChatClientSend {
    public static void main(String[] args) throws IOException {
        // Prompt the user for a chat message from stdin
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter your User Id:");
        String userId = scanner.nextLine();
        if (userId == null || userId.trim().isEmpty()) {
            System.out.println("No User Id provided. Exiting.");
            scanner.close();
            return; // Exit if no user ID is provided
        }

        System.out.println("Enter your chat message:");
        String input = scanner.nextLine();
        // Check if the input is empty
        if (input == null || input.trim().isEmpty()) {
            System.out.println("No chat message provided. Exiting.");
            scanner.close();
            return; // Exit if no input is provided
        }

        scanner.close();

        String workflowId = "chat-workflow-" + userId;

        try {
            WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                    .setTarget("localhost:7233")
                    .build();

            WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
            WorkflowClient client = WorkflowClient.newInstance(service);

            // Create the workflow client stub. It is used to start the workflow execution.
            WorkflowStub workflow = client.newUntypedWorkflowStub(workflowId);

            // Signal into the workflow with the chat message
            workflow.signal("prompt", input);

        } catch (Exception e) {
            System.err.println("Error starting workflow: " + e.getMessage());
            e.printStackTrace();
            return; // Exit if there is an error starting the workflow
        }

        System.exit(0);
    }
}
