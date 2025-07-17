package bitovi.activities;

import java.io.InputStream;
import java.util.IllegalFormatException;

import bitovi.common.Config;

import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

public class ActivitiesImpl implements Activities {
	@Override
	public String promptLLM(String userQuestion, String agentResponse) throws ApplicationFailure {
		Config config = new Config();

		String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
		String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
		String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");
		String AWS_REGION = config.getProperty("AWS_REGION");

		String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");

		try {
			BedrockRuntimeClient bedrockRuntimeClient = BedrockRuntimeClient.builder()
					.credentialsProvider(
							StaticCredentialsProvider.create(
									AwsSessionCredentials.create(
											AWS_ACCESS_KEY_ID,
											AWS_SECRET_ACCESS_KEY,
											AWS_SESSION_TOKEN)))
					.region(Region.of(AWS_REGION))
					.build();

			SystemContentBlock systemContent = SystemContentBlock.fromText(
					"You are a customer service grading agent. Your task is to evaluate customer service agent responses to customer inquiries.");

			InputStream promptStream = getClass().getClassLoader().getResourceAsStream("prompt.txt");
			String promptTemplate = new String(promptStream.readAllBytes()).trim().replaceAll("%(?!s)", "%%");

			String prompt = String.format(promptTemplate, userQuestion, agentResponse);

			Message policyMessage = Message.builder()
					.role(ConversationRole.USER)
					.content(ContentBlock.fromText(prompt))
					.build();

			Message prefillMessage = Message.builder()
					.role(ConversationRole.ASSISTANT)
					.content(ContentBlock.fromText("{"))
					.build();

			InferenceConfiguration inferenceConfig = InferenceConfiguration.builder()
					.maxTokens(2000)
					.temperature(1.0f)
					.build();

			ConverseRequest converseRequest = ConverseRequest.builder()
					.modelId(AWS_MODEL_ID)
					.system(systemContent)
					.messages(policyMessage, prefillMessage)
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
