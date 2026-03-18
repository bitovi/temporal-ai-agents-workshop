package bitovi.activities;

import java.util.List;

import org.json.JSONObject;

import bitovi.common.Config;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.ListMemoriesRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.Memory;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemorySummary;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

public class BedrockImpl implements Bedrock {
	@Override
	public String checkBedrockConnection() throws ApplicationFailure {
		Config config = new Config();

		String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
		String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
		String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");
		String AWS_REGION = config.getProperty("AWS_REGION");

		String AWS_MODEL_ID = config.getProperty("AWS_MODEL_ID");
		String AWS_EMBEDDING_MODEL_ID = config.getProperty("AWS_EMBEDDING_MODEL_ID");

		StaticCredentialsProvider credentialsProvider;

		validateAWSFormat(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN);

		try {
			if (AWS_SESSION_TOKEN == null || AWS_SESSION_TOKEN.isEmpty()) {
				System.out.println("Using long-term AWS credentials.");
				credentialsProvider = StaticCredentialsProvider.create(
						AwsBasicCredentials.create(
								AWS_ACCESS_KEY_ID,
								AWS_SECRET_ACCESS_KEY));
			} else {
				System.out.println("Using temporary AWS credentials.");
				credentialsProvider = StaticCredentialsProvider.create(
						AwsSessionCredentials.create(
								AWS_ACCESS_KEY_ID,
								AWS_SECRET_ACCESS_KEY,
								AWS_SESSION_TOKEN));
			}
		} catch (Exception e) {
			System.err.println("Error creating AWS credentials: " + e.getMessage());
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to create AWS credentials: " + e.getMessage(),
					"AWSCredentialsError");
		}

		BedrockRuntimeClient bedrockRuntimeClient;

		try {
			bedrockRuntimeClient = BedrockRuntimeClient.builder()
					.credentialsProvider(credentialsProvider)
					.region(Region.of(AWS_REGION))
					.build();

		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to create Bedrock client: " + e.getMessage(),
					"BedrockClientError");
		}

		try {
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
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to converse with Bedrock: " + e.getMessage(),
					"BedrockError");
		}

		try {
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
					"Failed to embed with Bedrock: " + e.getMessage(),
					"BedrockError");
		}

		return "Bedrock connection successful.";
	}

	@Override

	public String checkBedrockMemoryConnection() throws ApplicationFailure {
		Config config = new Config();

		String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
		String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
		String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");

		String AWS_BEDROCK_AGENTCORE_MEMORY_REGION = config.getProperty("AWS_BEDROCK_AGENTCORE_MEMORY_REGION");
		String AWS_BEDROCK_AGENTCORE_MEMORY_ID = config.getProperty("AWS_BEDROCK_AGENTCORE_MEMORY_ID");

		StaticCredentialsProvider credentialsProvider;

		validateAWSFormat(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN);

		try {
			if (AWS_SESSION_TOKEN == null || AWS_SESSION_TOKEN.isEmpty()) {
				System.out.println("Using long-term AWS credentials.");
				credentialsProvider = StaticCredentialsProvider.create(
						AwsBasicCredentials.create(
								AWS_ACCESS_KEY_ID,
								AWS_SECRET_ACCESS_KEY));
			} else {
				System.out.println("Using temporary AWS credentials.");
				credentialsProvider = StaticCredentialsProvider.create(
						AwsSessionCredentials.create(
								AWS_ACCESS_KEY_ID,
								AWS_SECRET_ACCESS_KEY,
								AWS_SESSION_TOKEN));
			}
		} catch (Exception e) {
			System.err.println("Error creating AWS credentials: " + e.getMessage());
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to create AWS credentials: " + e.getMessage(),
					"AWSCredentialsError");
		}

		BedrockAgentCoreControlClient bedrockAgentCoreControlClient;

		try {
			bedrockAgentCoreControlClient = BedrockAgentCoreControlClient.builder()
					.credentialsProvider(credentialsProvider)
					.region(Region.of(AWS_BEDROCK_AGENTCORE_MEMORY_REGION))
					.build();

		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to create Bedrock client: " + e.getMessage(),
					"BedrockClientError");
		}

		ListMemoriesRequest listMemoriesRequest = ListMemoriesRequest.builder().build();
		try {
			List<MemorySummary> memories = bedrockAgentCoreControlClient.listMemories(listMemoriesRequest).memories();
			if (!memories.isEmpty()) {
				for (MemorySummary memorySummary : memories) {
					System.out.println("Found Bedrock Memory Resource: " + memorySummary.id());
				}
			}
		} catch (Exception e) {
			System.err.println("Failed to list Bedrock memories: " + e.getMessage());
		}

		GetMemoryRequest getRequest = GetMemoryRequest.builder()
				.memoryId(AWS_BEDROCK_AGENTCORE_MEMORY_ID)
				.build();

		try {
			Memory memory = bedrockAgentCoreControlClient.getMemory(getRequest).memory();
			return "Bedrock Memory connection successful: " + memory.name();

		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to get Bedrock memory: " + e.getMessage(),
					"BedrockError");
		}
	}

	private void validateAWSFormat(String AWS_ACCESS_KEY_ID, String AWS_SECRET_ACCESS_KEY, String AWS_SESSION_TOKEN) {
		if (AWS_ACCESS_KEY_ID.startsWith("\"") || AWS_SECRET_ACCESS_KEY.startsWith("\"") ||
				(AWS_SESSION_TOKEN != null && AWS_SESSION_TOKEN.startsWith("\""))) {
			System.err.println("Warning: AWS credentials in .env file should not have quotes around the values.");
		}
	}
}