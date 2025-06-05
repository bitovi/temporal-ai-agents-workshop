package bitovi.providers;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
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

    private final static String MODEL_ID = "meta.llama3-1-8b-instruct-v1:0";
    private final static String MODEL_ARN = "arn:aws:bedrock:us-east-2:755521597925:inference-profile/us.meta.llama3-1-8b-instruct-v1:0";

    private BedrockClient bedrockClient;
    private BedrockRuntimeClient bedrockRuntimeClient;
    private final Region region = Region.US_EAST_2; // Default region, can be changed as needed

    public BedrockProvider() {
        this.bedrockClient = BedrockClient.builder()
                .credentialsProvider(
                        ProfileCredentialsProvider.builder().profileName("BitoviSandbox")
                                .build())
                .region(region)
                .build();

        this.bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .credentialsProvider(ProfileCredentialsProvider.builder().profileName("BitoviSandbox")
                        .build())
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
                    .modelId(MODEL_ARN)
                    .body(SdkBytes.fromUtf8String(jsonBody.toString()))
                    .build());

            String utf8 = invokeResponse.body().asUtf8String();
            String completion = new JSONObject(utf8)
                    .getString("generation");

            return completion;
        } catch (SdkClientException e) {
            System.err.printf("ERROR: Can't invoke '%s'. Reason: %s", MODEL_ID, e.getMessage());
            throw new LLMProviderException(e.getMessage());
        }
    }

    @Override
    public LLMProviderChatMessage chat(ArrayList<LLMProviderChatMessage> prompt) throws LLMProviderException {
        // TODO: Implement chat functionality for BedrockProvider instead of faking it with completion.

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
