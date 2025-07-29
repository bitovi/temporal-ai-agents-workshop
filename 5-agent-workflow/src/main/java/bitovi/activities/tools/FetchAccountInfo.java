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

public class FetchAccountInfo {
    public static ToolResult execute(String toolName, Map<String, Object> toolUseInput) {
        System.out.println("Executing FetchAccountInfo with inputs: " + toolUseInput);

        if (toolUseInput == null || !toolUseInput.containsKey("accountId")) {
            throw new IllegalArgumentException("Invalid input: 'accountId' is required.");
        }

        String accountId = toolUseInput.get("accountId").toString();
        System.out.println("Fetching account info for accountId: " + accountId);
        return new ToolResult("{\"accountId\":\"" + accountId + "\",\"name\":\"John Doe\",\"email\":\"john.doe@example.com\"}", false);
    }

    public static Tool getBedrockTool() {
        Map<String, Document> numPropertyMap = new HashMap<>();
        numPropertyMap.put("type", Document.fromString("number"));
        numPropertyMap.put("description", Document.fromString("The account ID to fetch the information for."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("accountId", Document.fromMap(numPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("accountId"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("get_account_info")
                .description("Gets the account information for a specific account ID.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
