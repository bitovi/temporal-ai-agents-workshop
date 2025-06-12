package bitovi.common.tools.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class MCPBedrockTool {

    public static Tool transform(io.modelcontextprotocol.spec.McpSchema.Tool tool) {
        System.out.println("Transforming MCP Tool: " + tool.name());
        System.out.println("Description: " + tool.description());
        JsonSchema js = tool.inputSchema();

        Map<String, Document> propertiesMap = new HashMap<>();
        List<Document> requiredList = new ArrayList<>();

        Map<String, Object> properties = js.properties();
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Tool must have at least one property in the input schema");
        }

        // Iterate over each property in the JSON Schema
        properties.forEach((propertyName, objectTypeDescription) -> {
            // Ensure the value is a Map<String, String> representing the JSON schema
            if (!(objectTypeDescription instanceof HashMap)) {
                throw new IllegalArgumentException("Expected value to be a HashMap<String, Document>");
            }

            @SuppressWarnings("unchecked")
            HashMap<String, String> valueSchema = (HashMap<String, String>) objectTypeDescription;

            // Convert the JSON schema to a Document
            Map<String, Document> propertyMap = new HashMap<>();

            if (valueSchema.containsKey("type")) {
                String type = valueSchema.get("type");
                propertyMap.put("type", Document.fromString(type));
            } else {
                throw new IllegalArgumentException("Each property must contain 'type'");
            }

            if (valueSchema.containsKey("description")) {
                propertyMap.put("description", Document.fromString(valueSchema.get("description")));
            }

            // Add the property to the properties map
            System.out.println("Property Found: " + propertyName + " with type: " + valueSchema.get("type"));
            propertiesMap.put(propertyName, Document.fromMap(propertyMap));
        });

        js.required().forEach(requiredField -> {
            // Ensure the required fields are added to the properties map
            if (propertiesMap.containsKey(requiredField)) {
                System.out.println("Required field: " + requiredField);
                requiredList.add(Document.fromString(requiredField));
            }
        });

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
