package bitovi.activities.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class WeatherTool {
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
        numPropertyMap.put("description", Document.fromString("The zip code to fetch the weather for."));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("zipCode", Document.fromMap(numPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("zipCode"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Now create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name("get_weather")
                .description("Gets the current weather for a specific location by zip code.")
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
