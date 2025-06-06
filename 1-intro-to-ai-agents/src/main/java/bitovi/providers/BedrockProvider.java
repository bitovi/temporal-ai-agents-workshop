package bitovi.providers;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import bitovi.Config;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsResponse;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

public class BedrockProvider implements LLMProvider {

    private String AWS_MODEL_ID;
    private String AWS_MODEL_ARN;

    private BedrockClient bedrockClient;
    private BedrockRuntimeClient bedrockRuntimeClient;
    private final Region region = Region.US_EAST_2; // Default region, can be changed as needed

    public BedrockProvider() {
        this.AWS_MODEL_ID = Config.getProperty("AWS_MODEL_ID");
        this.AWS_MODEL_ARN = Config.getProperty("AWS_MODEL_ARN");

        String AWS_ACCESS_KEY_ID = Config.getProperty("AWS_ACCESS_KEY_ID");
        String AWS_SECRET_ACCESS_KEY = Config.getProperty("AWS_SECRET_ACCESS_KEY");
        String AWS_SESSION_TOKEN = Config.getProperty("AWS_SESSION_TOKEN");

        this.bedrockClient = BedrockClient.builder()
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsSessionCredentials.create(
                                        AWS_ACCESS_KEY_ID,
                                        AWS_SECRET_ACCESS_KEY,
                                        AWS_SESSION_TOKEN)))
                .region(region)
                .build();

        this.bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(
                                AWS_ACCESS_KEY_ID,
                                AWS_SECRET_ACCESS_KEY,
                                AWS_SESSION_TOKEN)))
                .region(region)
                .build();
    }

    @Override
    public ArrayList<String> getModels() throws LLMProviderException {
        ArrayList<String> modelNames = new ArrayList<>();
        try {
            ListFoundationModelsResponse response = bedrockClient.listFoundationModels(r -> {
            });

            List<FoundationModelSummary> models = response.modelSummaries();

            if (models.isEmpty()) {
                System.out.println("No available models in " + region.toString());
            } else {
                for (FoundationModelSummary model : models) {
                    modelNames.add(model.modelId());
                }
            }

            return modelNames;

        } catch (SdkClientException e) {
            System.err.println(e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public String completion(String prompt) throws LLMProviderException {
        try {
            JSONObject jsonBody = new JSONObject()
                    .put("prompt", prompt)
                    .put("temperature", 0.5F);

            InvokeModelResponse invokeResponse = this.bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
                    .modelId(AWS_MODEL_ARN)
                    .body(SdkBytes.fromUtf8String(jsonBody.toString()))
                    .build());

            String utf8 = invokeResponse.body().asUtf8String();
            String completion = new JSONObject(utf8)
                    .getString("generation");

            return completion;
        } catch (SdkClientException e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", AWS_MODEL_ID, e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public LLMProviderChatMessage chat(ArrayList<LLMProviderChatMessage> prompt) throws LLMProviderException {
        // TODO: Implement chat functionality for BedrockProvider instead of faking it
        // with completion.

        // Convert the chat messages to a single prompt string
        StringBuilder promptBuilder = new StringBuilder();
        for (LLMProviderChatMessage message : prompt) {
            promptBuilder.append(message.getRole()).append(": ").append(message.getContent()).append("\n\n");
        }
        String completion = this.completion(promptBuilder.toString());

        // Create a new chat message with the model's response
        LLMProviderChatMessage responseMessage = new LLMProviderChatMessage("assistant", completion);
        return responseMessage;
    }

    @Override
    public List<List<Double>> embedding(List<String> inputs) throws LLMProviderException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'embedding'");
    }

}
