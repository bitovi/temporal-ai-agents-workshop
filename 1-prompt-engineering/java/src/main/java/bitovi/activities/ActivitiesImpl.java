package bitovi.activities;

import java.io.InputStream;
import java.util.IllegalFormatException;

import bitovi.common.AWS;
import bitovi.common.Config;

import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;

public class ActivitiesImpl implements Activities {
	@Override
	public String promptLLM(String userQuestion, String agentResponse) throws ApplicationFailure {
		Config config = new Config();
		String AWS_REGION = config.getProperty("AWS_REGION");
		String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");

		try {
			AwsCredentialsProvider credentialsProvider = AWS.getAwsCredentialsProvider();

			BedrockRuntimeClient bedrockRuntimeClient = BedrockRuntimeClient.builder()
					.credentialsProvider(credentialsProvider)
					.region(Region.of(AWS_REGION))
					.build();

			InputStream promptStream = getClass().getClassLoader().getResourceAsStream("prompt.txt");
			String promptTemplate = new String(promptStream.readAllBytes()).trim().replaceAll("%(?!s)", "%%");

			String prompt = String.format(promptTemplate, userQuestion, agentResponse);

			Message policyMessage = Message.builder()
					.role(ConversationRole.USER)
					.content(ContentBlock.fromText(prompt))
					.build();

			InferenceConfiguration inferenceConfig = InferenceConfiguration.builder()
					.maxTokens(2000)
					.temperature(1.0f)
					.build();

			ConverseRequest converseRequest = ConverseRequest.builder()
					.modelId(AWS_MODEL_ID)
					.messages(policyMessage)
					.inferenceConfig(inferenceConfig)
					.build();

			ConverseResponse response = bedrockRuntimeClient.converse(converseRequest);
			String completion = response.output().message().content().get(0).text();
			System.out.println("LLM Response: " + completion);

			return "LLM Response (Claude): " + completion;
		} catch (IllegalFormatException e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to format prompt: " + e.getMessage(),
					"PromptFormattingError");
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Bedrock: " + e.getMessage(),
					"BedrockError");
		}
	};
}
