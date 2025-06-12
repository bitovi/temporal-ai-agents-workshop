package bitovi.common.tools;

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
        // For each type/description pair in the tool's properties...

        JsonSchema js = tool.inputSchema();

        Map<String, Document> propertiesMap = new HashMap<>();
        List<Document> requiredList = new ArrayList<>();

        js.properties().forEach((key, value) -> {
            // Convert the JSON schema to a Document
            Map<String, Document> propertyMap = new HashMap<>();
            // I know that the shape here is an object with "type" and "description"
            // from the MCP JSON Schema Spec. How do we cast/handle this?
            propertyMap.put("type", Document.fromString(value.type()));
            propertyMap.put("description", Document.fromString(value.description()));

            // Add the property to the properties map
            propertiesMap.put(key, Document.fromMap(propertyMap));
        });

        js.required().forEach(requiredField -> {
            // Ensure the required fields are added to the properties map
            if (!propertiesMap.containsKey(requiredField)) {
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

    public static Tool getBedrockTool() {
        Map<String, Document> numPropertyMap = new HashMap<>();
        numPropertyMap.put("type", Document.fromString("number"));
        numPropertyMap.put("description", Document.fromString("The number to calculate the cosine of."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("num", Document.fromMap(numPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("num"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("calculate_cosine")
                .description("Get the cosine of a number.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }

}
