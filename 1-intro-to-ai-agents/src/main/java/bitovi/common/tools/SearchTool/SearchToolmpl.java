package bitovi.common.tools.SearchTool;

import bitovi.Config;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class SearchToolmpl {
    public static Tool getBedrockToolSpecification() {

        // Read in the JSON file to get the tool definition
        String contents;
        try {
            contents = Config.readFile("src/main/java/bitovi/common/tools/SearchTool/definition.json");
        } catch (Exception e) {
            System.err.println("Error reading tool definition file: " + e.getMessage());
            return null;
        }

        return Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name("web_search")
                        .description("Search the web for a query string")
                        .inputSchema(ToolInputSchema.builder()
                                .json(Document.fromString(contents))
                                .build())
                        .build())
                .build();
    }

    public static String executeTool(String query) {
        return "Search results for: " + query;
    }
}
