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

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class FetchWebpageTool {
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        System.out.println("Executing FetchWebpageTool with inputs: " + toolUseInput);

        if (toolUseInput == null || !toolUseInput.containsKey("url")) {
            throw new IllegalArgumentException("Invalid input: 'url' is required.");
        }

        String url = toolUseInput.get("url").toString();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            System.err.println("Error fetching webpage: " + e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public static Tool getBedrockTool() {
        // Define 'url' property (string, required)
        Map<String, Document> urlPropertyMap = new HashMap<>();
        urlPropertyMap.put("type", Document.fromString("string"));
        urlPropertyMap.put("description", Document.fromString("The URL of the webpage to fetch."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("url", Document.fromMap(urlPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("url"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("fetch_webpage")
                .description("Fetch the content of a webpage given its URL. Uses a simple GET request. No JavaScript execution.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
