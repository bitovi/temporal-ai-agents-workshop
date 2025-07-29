package bitovi.activities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitovi.common.AWS.ToolResult;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class SearchWeb {
    public static ToolResult execute(String toolName, Map<String, Object> toolUseInput) {
        System.out.println("Executing SearchWeb with inputs: " + toolUseInput);

        if (toolUseInput == null || !toolUseInput.containsKey("query")) {
            throw new IllegalArgumentException("Invalid input: 'query' is required.");
        }

        String query = toolUseInput.get("query").toString();
        System.out.println("Searching the web for query: " + query);
        return new ToolResult( "{\"results\":[{\"title\":\"Example Result\",\"url\":\"https://example.com\"}]}", false);
    }

    public static Tool getBedrockTool() {
        Map<String, Document> strPropertyMap = new HashMap<>();
        strPropertyMap.put("type", Document.fromString("string"));
        strPropertyMap.put("description", Document.fromString("The search query to use."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("query", Document.fromMap(strPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("query"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("search_web")
                .description("Searches the web for a specific query.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
