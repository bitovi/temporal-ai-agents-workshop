```java
BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();
ConverseResponse response = bedrockRuntimeClient.converse(request);

StringBuilder textResponse = new StringBuilder();
for (ContentBlock block : response.output().message().content()) {
    if (block.text() != null) {
        textResponse.append(block.text());
    }

    if (block.toolUse() != null) {
        ToolUseBlock toolUseBlock = block.toolUse();
        return new ModelResponse(null,
            new ModelToolCall(toolUseBlock.name(), toolInputs.toString())
        );
    }
}

return new ModelResponse(textResponse.toString(), null);
```
