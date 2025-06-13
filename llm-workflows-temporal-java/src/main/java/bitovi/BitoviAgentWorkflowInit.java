package bitovi;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.util.UUID;

import bitovi.workflows.AgentGoal.AgentGoalWorkflow;

public class BitoviAgentWorkflowInit {
    public static void main(String[] args) throws Exception {

        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("localhost:7233")
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

        WorkflowClient client = WorkflowClient.newInstance(service);

        // Run the agent workflow demo
        String workflowId = "agent-workflow" + UUID.randomUUID().toString();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue("default")
                .build();

        AgentGoalWorkflow workflow = client.newWorkflowStub(AgentGoalWorkflow.class, options);
        WorkflowStub untypedWorkflow = WorkflowStub.fromTyped(workflow);

        // Start the workflow
        untypedWorkflow.start();

        // Signal the workflow with a goal
        workflow.prompt("Hello, I need help with my project.");

        String result = untypedWorkflow.getResult(String.class);

        System.out.println(workflowId + " " + result);
        service.shutdown();
    }
}
