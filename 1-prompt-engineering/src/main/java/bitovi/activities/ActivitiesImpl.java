package bitovi.activities;

import bitovi.common.Config;

import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

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

			InputStream promptStream = getClass().getClassLoader().getResourceAsStream("policy-prompt.txt");
			String policyPrompt = new String(promptStream.readAllBytes()).trim().replaceAll("\\r?\\n", " ");

			String requestBody = String.format(
					"""
							{
								"anthropic_version": "bedrock-2023-05-31",
								"system": "You are a customer service grading agent. Your task is to evaluate customer service agent responses to customer inquiries.",
								"messages": [
									{
										"role": "user",
										"content": [
											{
												"type": "text",
												"text": "%s"
											}
										]
									},
									{
										"role": "user",
										"content": [
											{
												"type": "text",
												"text": "%s"
											}
										]
									},
									{
										"role": "user",
										"content": [
											{
												"type": "text",
												"text": "%s"
											}
										]
									}
								],
								"max_tokens": 500,
								"temperature": 0.7
							}
							""",
					policyPrompt, userQuestion, agentResponse);

			InvokeModelRequest request = InvokeModelRequest.builder()
					.modelId(AWS_MODEL_ID)
					.contentType("application/json")
					.accept("application/json")
					.body(SdkBytes.fromUtf8String(requestBody))
					.build();

			InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);
			String responseBody = response.body().asUtf8String();
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(responseBody);

			String completion = rootNode.at("/content/0/text").asText();
			System.out.println("LLM Response: " + completion);

			return "LLM Response (Claude): " + completion;
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailure(
					"Failed to connect to Bedrock: " + e.getMessage(),
					"BedrockError");
		}
	};
}
