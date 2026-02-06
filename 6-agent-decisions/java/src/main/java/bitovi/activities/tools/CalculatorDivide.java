package bitovi.activities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class CalculatorDivide {
    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        String a = toolUseInput.get("numerator").toString();
        String b = toolUseInput.get("denominator").toString();

        System.out.println("Executing " + toolName + " with inputs: " + toolUseInput);
        try {
            double numerator = Double.parseDouble(a);
            double denominator = Double.parseDouble(b);
            if (denominator == 0) {
                return "Error: Division by zero is not allowed.";
            }
            double quotient = numerator / denominator;
            return Double.toString(quotient);
        } catch (NumberFormatException e) {
            return "Error: Invalid number format.";
        }
    }

    public static Tool getBedrockTool() {
        // Define 'a' property (string, required)
        Map<String, Document> aPropertyMap = new HashMap<>();
        aPropertyMap.put("type", Document.fromString("string"));
        aPropertyMap.put("description", Document.fromString("The numerator for the division."));

        // Define 'b' property (string, required)
        Map<String, Document> bPropertyMap = new HashMap<>();
        bPropertyMap.put("type", Document.fromString("string"));
        bPropertyMap.put("description", Document.fromString("The denominator for the division."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("numerator", Document.fromMap(aPropertyMap));
        propertiesMap.put("denominator", Document.fromMap(bPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("numerator"));
        requiredList.add(Document.fromString("denominator"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        Document document = Document.fromMap(rootMap);
        ToolSpecification specification = ToolSpecification.builder()
                .name("division_calculator")
                .description(
                        "A simple calculator that divides two numbers. Provide two numbers as input and it will return their quotient.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();

    }
}
