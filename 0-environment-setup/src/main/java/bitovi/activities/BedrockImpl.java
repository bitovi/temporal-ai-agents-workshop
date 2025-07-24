package bitovi.activities;

import bitovi.common.Config;

import org.json.JSONObject;

import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;

public class BedrockImpl implements Bedrock {
	@Override
	public void checkBedrockConnection() throws ApplicationFailure {
		Config config = new Config();

		String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
		String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
		String AWS_REGION = config.getProperty("AWS_REGION");

		String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");
		String AWS_EMBEDDING_MODEL_ID = config.getProperty("AWS_EMBEDDING_MODEL_ID");

		try {
			BedrockRuntimeClient bedrockRuntimeClient = BedrockRuntimeClient.builder()
					.credentialsProvider(
							StaticCredentialsProvider.create(
									AwsBasicCredentials.create(
											AWS_ACCESS_KEY_ID,
											AWS_SECRET_ACCESS_KEY)))
					.region(Region.of(AWS_REGION))
					.build();

			ConverseRequest converseRequest = ConverseRequest.builder()
					.modelId(AWS_MODEL_ID)
					.messages(Message.builder()
							.role(ConversationRole.USER)
							.content(ContentBlock.fromText("Are you alive?"))
							.build())
					.inferenceConfig(interfaceConfig -> interfaceConfig
							.maxTokens(2000)
							.temperature(1.0f)
							.build())
					.build();
			bedrockRuntimeClient.converse(converseRequest);

			InvokeModelRequest embedRequest = InvokeModelRequest.builder()
					.modelId(AWS_EMBEDDING_MODEL_ID)
					.contentType("application/json")
					.accept("*/*")
					.body(SdkBytes.fromUtf8String(new JSONObject()
							.put("inputText", "Hello").toString()))
					.build();
			bedrockRuntimeClient.invokeModel(embedRequest);
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Bedrock: " + e.getMessage(),
					"BedrockError");
		}
	}
}