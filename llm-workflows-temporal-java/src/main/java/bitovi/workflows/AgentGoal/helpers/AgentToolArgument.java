package bitovi.workflows.AgentGoal.helpers;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.core.document.Document;

public class AgentToolArgument {
    private String name;
    private String description;
    private boolean required;
    private AgentToolArgumentType type;

    public AgentToolArgument(String name, String description, AgentToolArgumentType type, boolean required) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public AgentToolArgumentType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public Map<String, Document> getArgument() {
        Map<String, Document> propertyMap = new HashMap<>();
        propertyMap.put("type", Document.fromString(type.name().toLowerCase()));
        propertyMap.put("description", Document.fromString(description));
        return propertyMap;
    }
}
