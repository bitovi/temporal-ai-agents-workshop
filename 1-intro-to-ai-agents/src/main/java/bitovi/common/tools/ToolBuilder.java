package bitovi.common.tools;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class ToolBuilder {
    public static Tool buildBedrockToolSpecification(String name, String description, String json) {
        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(name)
                        .description(description)
                        .inputSchema(ToolInputSchema.builder()
                                .json(Document.fromString(json))
                                .build())
                        .build())
                .build();
    }
}
