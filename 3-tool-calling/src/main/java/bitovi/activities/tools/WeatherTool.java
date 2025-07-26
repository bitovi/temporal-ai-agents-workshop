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
    /**
     * Executes the weather tool with the provided inputs.
     * 
     * This function simulates fetching weather data based on the provided
     * inputs. In a real-world scenario, this would involve making an API call
     * to a weather service.
     * 
     * @param toolUseInput
     * @return
     */
    public static String execute(Document toolUseInput) {
        return "{\"coord\":{\"lon\":-122.4167,\"lat\":37.7813},\"weather\":[{\"id\":801,\"main\":\"Clouds\",\"description\":\"few clouds\",\"icon\":\"02d\"}],\"base\":\"stations\",\"main\":{\"temp\":291.97,\"feels_like\":291.84,\"temp_min\":289.87,\"temp_max\":295.05,\"pressure\":1012,\"humidity\":74,\"sea_level\":1012,\"grnd_level\":1009},\"visibility\":10000,\"wind\":{\"speed\":6.17,\"deg\":330},\"clouds\":{\"all\":20},\"dt\":1752258201,\"sys\":{\"type\":2,\"id\":2017837,\"country\":\"US\",\"sunrise\":1752238625,\"sunset\":1752291176},\"timezone\":-25200,\"id\":0,\"name\":\"San Francisco\",\"cod\":200}";
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
