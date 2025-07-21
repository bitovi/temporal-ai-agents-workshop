```java
ToolConfiguration.Builder toolConfig = ToolConfiguration.builder();
List<Tool> tools = new ArrayList<>();

tools.add(WeatherTool.getBedrockTool());

toolConfig.tools(tools);

ToolConfiguration toolConfiguration = toolConfig.build();
```
