package bitovi.common;

import java.util.ArrayList;

import bitovi.common.DataTypes.MCPServerDefinitionRecord;
import bitovi.workflows.AgentGoal.helpers.AgentToolDefinition;

public class Agent {
    public String id;
    public String categoryTag;
    public String agentName;
    public String agentDescription;
    public String starterPrompt;
    public String exampleConversation;

    public ArrayList<AgentToolDefinition> tools;

    public MCPServerDefinitionRecord _mcpServerDefinition;

    public Agent(String id, String categoryTag, String agentName, String agentDescription, String starterPrompt,
            String exampleConversation,
            ArrayList<AgentToolDefinition> tools) {
        this.id = id;
        this.categoryTag = categoryTag;
        this.agentName = agentName;
        this.agentDescription = agentDescription;
        this.starterPrompt = starterPrompt;
        this.exampleConversation = exampleConversation;

        if (tools != null) {
            this.tools = tools;
        } else {
            this.tools = new ArrayList<>();
        }
    }

    public Agent(String id) {
        this.id = id;
        this.categoryTag = null;
        this.agentName = null;
        this.agentDescription = null;
        this.starterPrompt = null;
        this.exampleConversation = null;
        this.tools = new ArrayList<>();
    }

    public MCPServerDefinitionRecord mcpServerDefinition() {
        return _mcpServerDefinition;
    }

    public void registerTool(AgentToolDefinition tool) {
        if (tool != null) {
            this.tools.add(tool);
        }
    }

    public void setDescription(String description) {
        this.agentDescription = description;
    }

    public void setName(String name) {
        this.agentName = name;
    }

    public void setCategoryTag(String categoryTag) {
        this.categoryTag = categoryTag;
    }

    public void setStarterPrompt(String starterPrompt) {
        this.starterPrompt = starterPrompt;
    }

    public void setExampleConversation(String exampleConversation) {
        this.exampleConversation = exampleConversation;
    }
}
