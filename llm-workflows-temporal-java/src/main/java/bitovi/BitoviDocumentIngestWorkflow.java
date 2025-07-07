package bitovi;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.util.Scanner;

import bitovi.workflows.DocumentIngest.DocumentIngest;

public class BitoviDocumentIngestWorkflow {
        public static void main(String[] args) throws Exception {

                // Prompt the user for a chat message from stdin
                Scanner scanner = new Scanner(System.in);
                System.out.println("Documents Directory:");
                String documentsDirectory = scanner.nextLine();
                if (documentsDirectory == null || documentsDirectory.trim().isEmpty()) {
                        System.out.println("No Documents Directory provided. Using default Docker path.");
                        documentsDirectory = "/usr/src/app/src/main/resources/documents/";
                }
                scanner.close();

                WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                                .setTarget("localhost:7233")
                                .build();

                WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

                WorkflowClient client = WorkflowClient.newInstance(service);

                String workflowId = "document-ingest" + documentsDirectory.replaceAll("[^a-zA-Z0-9]", "-");
                WorkflowOptions options = WorkflowOptions.newBuilder()
                                .setWorkflowId(workflowId)
                                .setTaskQueue("default")
                                .build();

                DocumentIngest workflow = client.newWorkflowStub(DocumentIngest.class, options);

                System.out.println("Starting workflow for document: " + documentsDirectory);
                // Start the workflow
                WorkflowClient.start(workflow::ingest, documentsDirectory);

                service.shutdown();
        }
}
