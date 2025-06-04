package bitovi;

import bitovi.common.GenericLLMProvider;
import bitovi.common.tools.WeatherTool;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.tools.Tools;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

public class RepkaStandalone {
        public static void main(String[] args) throws Exception {

                // Test some Bedrock

                BedrockClient bedrockClient = BedrockClient.builder()
                                .credentialsProvider(
                                                ProfileCredentialsProvider.builder().profileName("BitoviSandbox")
                                                                .build())
                                // .endpointOverride(new URI("http://localhost:4566")) // LocalStack only
                                // supports Bedrock on their Pro version. Grrr.
                                .region(Region.US_EAST_1)
                                .build();

                GenericLLMProvider.listFoundationModels(bedrockClient, Region.US_EAST_2);

                // GetInferenceProfileResponse response = bedrockClient
                //                 .getInferenceProfile(GetInferenceProfileRequest.builder().inferenceProfileIdentifier(
                //                                 "arn:aws:bedrock:us-east-2:755521597925:inference-profile/us.meta.llama3-1-8b-instruct-v1:0")
                //                                 .build());

                // System.out.println("Inference Profile ARN: " + response.inferenceProfileArn());

                // Create a Bedrock Runtime client in the AWS Region you want to use.
                // Replace the DefaultCredentialsProvider with your preferred credentials
                // provider.
                BedrockRuntimeClient bedrockRuntimeClient = BedrockRuntimeClient.builder()
                                .credentialsProvider(ProfileCredentialsProvider.builder().profileName("BitoviSandbox")
                                                .build())
                                .region(Region.US_EAST_2)
                                .build();

                GenericLLMProvider.converse(bedrockRuntimeClient,
                                "What is the purpose of a Hello World program? Explain in a brief paragraph.");

                System.out.println("Starting RepkaStandalone...");

                System.out.println("Creating Ollama Instance...");
                OllamaAPI ollamaAPI = GenericLLMProvider.getOllamaInstance();

                System.out.println("Selecting Model...");
                String modelName = GenericLLMProvider.getOllamaModel();
                OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(modelName);

                System.out.println("Registering Weather Tool...");
                final Tools.ToolSpecification weatherToolSpec = WeatherTool.getSpecification();

                ollamaAPI.registerTool(weatherToolSpec);

                System.out.println("Creating Chat Request...");
                OllamaChatRequest requestModel = builder
                                .withMessage(OllamaChatMessageRole.USER,
                                                "What is the weather in New York today?")
                                .build();

                System.out.println("Sending Chat Request...");
                OllamaChatResult chatResult = ollamaAPI.chat(requestModel);

                System.out.println("Chat Result Received.");
                System.out.println("First answer: " + chatResult.getResponseModel().getMessage().getContent());
        }
}
