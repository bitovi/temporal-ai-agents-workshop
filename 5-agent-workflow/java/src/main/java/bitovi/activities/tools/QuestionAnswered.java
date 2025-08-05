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

public class QuestionAnswered {
    public static ToolResult execute(String toolName, Map<String, Object> toolUseInput) {
        System.out.println("Executing QuestionAnswered with inputs: " + toolUseInput);

        if (toolUseInput == null || !toolUseInput.containsKey("answer")) {
            throw new IllegalArgumentException("Invalid input: 'answer' is required.");
        }

        String answer = toolUseInput.get("answer").toString();
        System.out.println("Answering the question with: " + answer);

        return new ToolResult(answer, true);
    }

    public static Tool getBedrockTool() {
        Map<String, Document> strPropertyMap = new HashMap<>();
        strPropertyMap.put("type", Document.fromString("string"));
        strPropertyMap.put("description", Document.fromString("The answer to the users original question."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("answer", Document.fromMap(strPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("answer"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("complete_question_answered")
                .description(
                        "Answers the users original question ending the agentic conversation. This will return to the user and stop execution. You should use this once the agent has reached a final solution or answer.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
