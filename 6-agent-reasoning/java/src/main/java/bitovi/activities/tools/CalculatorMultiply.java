package bitovi.activities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class CalculatorMultiply {
    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        String a = toolUseInput.get("a").toString();
        String b = toolUseInput.get("b").toString();

        System.out.println("Executing " + toolName + " with inputs: " + toolUseInput);
        try {
            double numA = Double.parseDouble(a);
            double numB = Double.parseDouble(b);
            double product = numA * numB;
            return Double.toString(product);
        } catch (NumberFormatException e) {
            return "Error: Invalid number format.";
        }
    }

    public static Tool getBedrockTool() {
        // Define 'a' property (string, required)
        Map<String, Document> aPropertyMap = new HashMap<>();
        aPropertyMap.put("type", Document.fromString("string"));
        aPropertyMap.put("description", Document.fromString("The first number to multiply."));

        // Define 'b' property (string, required)
        Map<String, Document> bPropertyMap = new HashMap<>();
        bPropertyMap.put("type", Document.fromString("string"));
        bPropertyMap.put("description", Document.fromString("The second number to multiply."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("a", Document.fromMap(aPropertyMap));
        propertiesMap.put("b", Document.fromMap(bPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("a"));
        requiredList.add(Document.fromString("b"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        Document document = Document.fromMap(rootMap);
        ToolSpecification specification = ToolSpecification.builder()
                .name("multiplication_calculator")
                .description(
                        "A simple calculator that multiplies two numbers together. Provide two numbers as input and it will return their product.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();

    }
}
