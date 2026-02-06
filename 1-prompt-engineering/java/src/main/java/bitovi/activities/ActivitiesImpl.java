package bitovi.activities;

import java.io.InputStream;
import java.util.IllegalFormatException;

import bitovi.common.AWS;
import bitovi.common.Config;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.Type;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

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

			if (response.output().message().hasContent() == false) {
				throw ApplicationFailure.newNonRetryableFailure(
						"Received empty response from Bedrock",
						"EmptyBedrockResponse");
			}

			StringBuilder sb = new StringBuilder();
			for (ContentBlock block : response.output().message().content()) {
				if (block.type() == Type.TEXT) {
					sb.append(block.text());
				}
			}

			String completion = sb.toString();
			System.out.println("LLM Response: " + completion);

			return completion;
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
