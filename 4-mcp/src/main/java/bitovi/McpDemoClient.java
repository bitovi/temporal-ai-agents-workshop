package bitovi;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import bitovi.common.ModelContextProtocolClient;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;

public class McpDemoClient {

    public static void main(String[] args) throws JsonProcessingException {
        ModelContextProtocolClient client = new ModelContextProtocolClient();

        client.getAvailableTools().forEach(tool -> {
            System.out.println("Tool Name: " + tool.toolSpec().name());
            System.out.println("Tool Description: " + tool.toolSpec().description());
            ToolInputSchema inputSchema = tool.toolSpec().inputSchema();
            if (inputSchema != null) {
                System.out.println("Input Schema: " + inputSchema.toString());
            } else {
                System.out.println("No Input Schema defined for this tool.");
            }
            System.out.println("-----------------------------");
        });

        Map<String, Object> arguments = Map.of(
                "zipCode", "14543");
        String result = client.executeMCPTool("weather-by-zip-code", arguments);

        System.out.println("Tool Execution Result: " + result);
    }
}
