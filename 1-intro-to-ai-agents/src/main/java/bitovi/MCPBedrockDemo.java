package bitovi;

import java.util.UUID;

import bitovi.workflows.MCPBedrock.MCPBedrockWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;

/**
 * Demo client showing AWS Bedrock Runtime integration with MCP server tools
 */
public class MCPBedrockDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("AWS Bedrock + MCP Server Tools Integration Demo");
        System.out.println("================================================");
        
        // Initialize Temporal client
        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget("localhost:7233")
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
        WorkflowClient client = WorkflowClient.newInstance(service);
        
        // Test queries that might use MCP tools
        String[] testQueries = {
            "What's the weather in San Francisco today?",
            "Can you calculate the cosine of 1.57 radians?",
            "What are the current stock prices?",
            "Tell me about the latest news"
        };
        
        for (String query : testQueries) {
            System.out.println("\n--- Processing Query ---");
            System.out.println("Query: " + query);
            
            try {
                String result = runWorkflow(client, query);
                System.out.println("Result: " + result);
            } catch (Exception e) {
                System.err.println("Error processing query: " + e.getMessage());
            }
        }
        
        service.shutdown();
    }
    
    private static String runWorkflow(WorkflowClient client, String query) throws Exception {
        String workflowId = "mcp-bedrock-demo-" + UUID.randomUUID().toString();
        
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue("default")
                .build();
        
        MCPBedrockWorkflow workflow = client.newWorkflowStub(MCPBedrockWorkflow.class, options);
        
        return workflow.processQueryWithMCPTools(query);
    }
}
