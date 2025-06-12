package bitovi.common.tools.transform;

import java.util.Map;

import bitovi.providers.MCPToolIntegration;
import io.github.ollama4j.tools.ToolFunction;
import io.github.ollama4j.tools.Tools;
import io.github.ollama4j.tools.Tools.PromptFuncDefinition.Property;
import io.github.ollama4j.tools.Tools.PromptFuncDefinition.Property.PropertyBuilder;
import io.github.ollama4j.tools.Tools.PromptFuncDefinition.Parameters.ParametersBuilder;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;

public class MCPOllamaTool {
        public static Tools.ToolSpecification transform(io.modelcontextprotocol.spec.McpSchema.Tool tool,
                        MCPToolIntegration mcpToolIntegration) {
                JsonSchema js = tool.inputSchema();

                Map<String, Object> properties = js.properties();
                if (properties == null || properties.isEmpty()) {
                        throw new IllegalArgumentException("Tool must have at least one property in the input schema");
                }

                Map<String, Property> ollamaProperties = new java.util.HashMap<>();

                properties.forEach((propertyName, propertyValue) -> {
                        if (propertyValue instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> propertyMap = (Map<String, Object>) propertyValue;
                                PropertyBuilder propertyBuilder = Property.builder()
                                                .type((String) propertyMap.get("type"))
                                                .description((String) propertyMap.get("description"));
                                ollamaProperties.put(propertyName, propertyBuilder.build());
                        }
                });

                ParametersBuilder pb = Tools.PromptFuncDefinition.Parameters.builder()
                                .type("object")
                                .properties(ollamaProperties)
                                .required(tool.inputSchema().required());

                return Tools.ToolSpecification.builder()
                                .functionName(tool.name())
                                .functionDescription(tool.description())
                                .toolFunction(MCPOllamaTool.buildToolHandler(tool.name(), mcpToolIntegration))
                                .toolPrompt(
                                                Tools.PromptFuncDefinition.builder()
                                                                .type("prompt")
                                                                .function(
                                                                                Tools.PromptFuncDefinition.PromptFuncSpec
                                                                                                .builder()
                                                                                                .name(tool.name())
                                                                                                .description(tool
                                                                                                                .description())
                                                                                                .parameters(pb.build())
                                                                                                .build())
                                                                .build())
                                .build();
        }

        public static ToolFunction buildToolHandler(String toolName, MCPToolIntegration mcpToolIntegration) {
                return (args) -> {
                        try {
                                return mcpToolIntegration.executeMCPTool(
                                                toolName,
                                                args);
                        } catch (Exception e) {
                                System.out.println(
                                                "Failed to execute MCP tool " + toolName + ": " + e.getMessage());
                                return null;
                        }
                };
        }
}
