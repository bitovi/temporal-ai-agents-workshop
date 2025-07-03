package bitovi;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.util.List;
import java.util.UUID;

import bitovi.workflows.DocumentIngest.DocumentIngest;

public class BitoviDocumentIngestWorkflow {
        public static void main(String[] args) throws Exception {
                WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                                .setTarget("localhost:7233")
                                .build();

                WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

                WorkflowClient client = WorkflowClient.newInstance(service);

                // These files are in one level above the root of the project
                List<String> documentPaths = List.of("Account-Deactivation-and-Deletion.txt", "Account-Transfer.txt",
                                "Changing-Your-Riot-ID.txt", "Protecting-Your-Account.txt",
                                "Requesting-Your-Account-Data.txt");

                for (String documentPath : documentPaths) {
                        String workflowId = "agent-workflow" + UUID.randomUUID().toString();
                        WorkflowOptions options = WorkflowOptions.newBuilder()
                                        .setWorkflowId(workflowId)
                                        .setTaskQueue("default")
                                        .build();

                        DocumentIngest workflow = client.newWorkflowStub(DocumentIngest.class, options);

                        String completePath = "src/main/resources/documents/" + documentPath;
                        System.out.println("Starting workflow for document: " + completePath);
                        // Start the workflow
                        WorkflowClient.start(workflow::ingest, completePath);

                }

                service.shutdown();
        }
}
