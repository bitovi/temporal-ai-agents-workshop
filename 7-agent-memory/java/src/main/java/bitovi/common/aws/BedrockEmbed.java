package bitovi.common.aws;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.json.JSONObject;

import bitovi.common.Config;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

public class BedrockEmbed {

        private static Config config = new Config();

        public static List<Float> calculateEmbedding(String input) {
                String AWS_EMBEDDING_MODEL_ID = config.getProperty("AWS_EMBEDDING_MODEL_ID");

                JSONObject jsonBody = new JSONObject()
                                .put("inputText", input);

                SdkBytes body = SdkBytes.fromUtf8String(jsonBody.toString());
                InvokeModelRequest request = InvokeModelRequest.builder()
                                .modelId(AWS_EMBEDDING_MODEL_ID)
                                .contentType("application/json")
                                .accept("*/*")
                                .body(body)
                                .build();

                BedrockRuntimeClient bedrockRuntimeClient = AWS.getBedrockRuntimeClient();
                InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);

                JSONObject responseJson = new JSONObject(
                                response.body().asString(StandardCharsets.UTF_8));

                List<Float> embedding = responseJson.getJSONArray("embedding").toList().stream()
                                .map(obj -> ((Number) obj).floatValue())
                                .toList();

                if (embedding.isEmpty()) {
                        return List.of();
                }

                return embedding;
        }
}
