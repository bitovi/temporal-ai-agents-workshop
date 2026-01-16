package bitovi.activities.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bitovi.common.Config;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class BraveSearchTool {
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        System.out.println("Executing BraveSearchTool with inputs: " + toolUseInput);

        if (toolUseInput == null || !toolUseInput.containsKey("q")) {
            throw new IllegalArgumentException("Invalid input: 'q' is required.");
        }

        String query = toolUseInput.get("q").toString();
        String count = toolUseInput.containsKey("count") ? toolUseInput.get("count").toString() : "10";
        
        Config config = new Config();
        String apiKey = config.getProperty("BRAVE_SEARCH_API_KEY");
        
        if (apiKey == null || apiKey.isEmpty()) {
            return "{\"error\": \"BRAVE_SEARCH_API_KEY not configured\"}";
        }

        try {
            String url = "https://api.search.brave.com/res/v1/web/search?q=" + 
                        java.net.URLEncoder.encode(query, "UTF-8") + 
                        "&count=" + count;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .header("X-Subscription-Token", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error executing Brave Search: " + e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static Tool getBedrockTool() {
        // Define 'q' property (string, required)
        Map<String, Document> qPropertyMap = new HashMap<>();
        qPropertyMap.put("type", Document.fromString("string"));
        qPropertyMap.put("description", Document.fromString("The search query string."));

        // Define 'count' property (number, optional)
        Map<String, Document> countPropertyMap = new HashMap<>();
        countPropertyMap.put("type", Document.fromString("number"));
        countPropertyMap.put("description", Document.fromString("Number of results to return. Defaults to 10."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("q", Document.fromMap(qPropertyMap));
        propertiesMap.put("count", Document.fromMap(countPropertyMap));

        // Create the "required" array (only 'q' is required)
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("q"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("brave_search")
                .description("Search the Internet using the Brave Search Engine API.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
