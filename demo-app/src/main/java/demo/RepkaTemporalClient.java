package demo;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

import java.util.UUID;

import demo.workflows.AgentGoal.AgentGoalWorkflow;
import demo.workflows.UserGreeting.UserGreetingWorkflow;

public class RepkaTemporalClient {
    public static void main(String[] args) throws Exception {

        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("localhost:7233")
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

        WorkflowClient client = WorkflowClient.newInstance(service);

        // Run the greeting workflow with a random UUID to ensure uniqueness
        runGreetingsWorkflow(client, "Mark");

        // Run the agent workflow demo
        runAgentWorkflow(client);

        service.shutdown();
    }

    private static void runGreetingsWorkflow(WorkflowClient wc, String name) throws Exception {
        String workflowId = "greeting-workflow" + UUID.randomUUID().toString();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue("default")
                .build();

        UserGreetingWorkflow workflow = wc.newWorkflowStub(UserGreetingWorkflow.class, options);

        String greeting = workflow.greetSomeone(name);
        System.out.println(workflowId + " " + greeting);
    }

    private static void runAgentWorkflow(WorkflowClient wc) throws Exception {
        String workflowId = "agent-workflow" + UUID.randomUUID().toString();

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue("default")
                .build();

        AgentGoalWorkflow workflow = wc.newWorkflowStub(AgentGoalWorkflow.class, options);
        WorkflowStub untypedWorkflow = WorkflowStub.fromTyped(workflow);

        // Start the workflow
        untypedWorkflow.start();

        // Signal the workflow with a goal
        workflow.prompt("Hello, I need help with my project.");

        String result = untypedWorkflow.getResult(String.class);

        System.out.println(workflowId + " " + result);
    }
}
