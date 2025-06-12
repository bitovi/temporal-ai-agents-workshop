package bitovi.common.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class CosineToolImpl {

    public static String execute(Document toolUseInput) {
        Map<String, Document> map = toolUseInput.asMap();
        Document documentNum = map.get("num");
        String strNumber = documentNum.asString();
        double number = Double.parseDouble(strNumber);

        return String.valueOf(Math.cos(number));
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

    public static io.github.ollama4j.tools.Tools.ToolSpecification getOllamaTool() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getOllamaTool'");
    }
}
