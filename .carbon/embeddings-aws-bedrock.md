```java
public static List<Float> calculateEmbedding(String input) {
    JSONObject jsonBody = new JSONObject().put("inputText", input);

    SdkBytes body = SdkBytes.fromUtf8String(jsonBody.toString());
    InvokeModelRequest request = InvokeModelRequest.builder()
            .modelId("amazon.titan-embed-text-v2:0").body(body).build();

    BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();
    InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);
    JSONObject responseJson = new JSONObject(response.body().asString(StandardCharsets.UTF_8));

    List<Float> embedding = responseJson.getJSONArray("embedding").toList().stream()
            .map(obj -> ((Number) obj).floatValue()).toList();
    return embedding;
}
```
