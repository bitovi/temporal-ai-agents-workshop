package bitovi.activities.tools;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;

/**
 * This class defines a custom tool that can be used as part of the Tool Calling demo.
 * 
 * Define your own tool here by implementing the getBedrockTool method to 
 * specify the tool's functionality and input schema.
 * 
 * You can then try out the workflow by asking the model to use this tool.
 * 
 * You can see that the String returned by the execute method will be passed
 * to the model as the tool's output.
 */
public class DefineYourOwnTool {
    public static String execute(Document toolUseInput) {
        // TODO: Implement the tool execution logic for DefineYourOwnTool
        throw new UnsupportedOperationException("This tool is not implemented yet.");
    }

    public static Tool getBedrockTool() {
        // TODO: Implement the tool specification for DefineYourOwnTool
        throw new UnsupportedOperationException("This tool is not implemented yet.");
    }
}
