package bitovi.common.tools.ConsineTool;

import bitovi.Config;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class CosineToolImpl {
    public static Tool getBedrockToolSpecification() {

        // Read in the JSON file to get the tool definition
        String contents;
        try {
            contents = Config.readFile("src/main/java/bitovi/common/tools/ConsineTool/definition.json");
            System.out.println("Loaded tool definition: " + contents);
        } catch (Exception e) {
            System.err.println("Error reading tool definition file: " + e.getMessage());
            return null;
        }

        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name("calculate_cosine")
                        .description("Calculate the cosine of a number in radians")
                        .inputSchema(ToolInputSchema.builder()
                                .json(Document.fromString(contents))
                                .build())
                        .build())
                .build();
    }

    public static double calculateCosine(double radians) {
        return Math.cos(radians);
    }
}
