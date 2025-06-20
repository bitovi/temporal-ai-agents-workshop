package bitovi.workflows.AgentGoal.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

public class AgentToolDefinition {
    private String toolName;
    private String toolDescription;
    private List<AgentToolArgument> toolArguments;

    public AgentToolDefinition(String toolName, String toolDescription, List<AgentToolArgument> toolArguments) {
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }
        this.toolName = toolName;

        if (toolDescription == null || toolDescription.isEmpty()) {
            throw new IllegalArgumentException("Tool description cannot be null or empty");
        }
        this.toolDescription = toolDescription;

        // Arguments CAN be null or empty, but we ensure it's always a valid list.
        if (toolArguments == null || toolArguments.isEmpty()) {
            this.toolArguments = new ArrayList<>();
        } else {
            this.toolArguments = new ArrayList<>(toolArguments);
        }
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolDescription() {
        return toolDescription;
    }

    public List<AgentToolArgument> getToolArguments() {
        return toolArguments;
    }

    public Tool getBedrockTool() {
        Map<String, Document> propertiesMap = new HashMap<>();
        List<Document> requiredList = new ArrayList<>();

        for (AgentToolArgument argument : toolArguments) {
            propertiesMap.put(argument.getName(), Document.fromMap(argument.getArgument()));
            if (argument.isRequired()) {
                requiredList.add(Document.fromString(argument.getName()));
            }
        }

        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        Document document = Document.fromMap(rootMap);

        ToolSpecification specification = ToolSpecification.builder()
                .name(this.toolName)
                .description(this.toolDescription)
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
