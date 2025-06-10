package bitovi;

import bitovi.activities.AgentGoalsActivitiesImpl;
import bitovi.activities.ChatActivitiesImpl;
import bitovi.activities.CompletionsImpl;
// import bitovi.activities.MCPBedrockActivitiesImpl;
import bitovi.workflows.AgentGoal.AgentGoalWorkflowImpl;
import bitovi.workflows.Chat.ChatWorkflowImpl;
// import bitovi.workflows.MCPBedrock.MCPBedrockWorkflowImpl;
import bitovi.workflows.UserGreeting.UserGreetingWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class BitoviTemporalWorker {

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
        worker.registerWorkflowImplementationTypes(AgentGoalWorkflowImpl.class);
        worker.registerWorkflowImplementationTypes(ChatWorkflowImpl.class);
        // worker.registerWorkflowImplementationTypes(MCPBedrockWorkflowImpl.class);

        // Register the activities with the worker for completions
        worker.registerActivitiesImplementations(new CompletionsImpl());

        // Register the activities with the worker for agent goals
        worker.registerActivitiesImplementations(new AgentGoalsActivitiesImpl());

        // Register the activities with the worker for chat
        worker.registerActivitiesImplementations(new ChatActivitiesImpl());

        // Register the activities with the worker for MCP-Bedrock integration
        // worker.registerActivitiesImplementations(new MCPBedrockActivitiesImpl());

        factory.start();

        // Keep the worker running
    }
}
