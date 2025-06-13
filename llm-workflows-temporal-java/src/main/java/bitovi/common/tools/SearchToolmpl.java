package bitovi.common.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.ollama4j.tools.Tools;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class SearchToolmpl {

    public static String execute(Document toolUseInput) {
        Map<String, Document> map = toolUseInput.asMap();
        Document queryDoc = map.get("query");
        String query = queryDoc.asString();

        return "" + query + " - This is a placeholder for the search result.";
    }

    public static Tool getBedrockTool() {
        Map<String, Document> queryPropertyMap = new HashMap<>();
        queryPropertyMap.put("type", Document.fromString("string"));
        queryPropertyMap.put("description", Document.fromString("The search query string."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("query", Document.fromMap(queryPropertyMap));

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
                .name("web_search")
                .description("Search the web using SearXNG for a specified query.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }

    public static Tools.ToolSpecification getOllamaTool() {
        throw new UnsupportedOperationException("Unimplemented method 'getOllamaTool'");
    }
}
