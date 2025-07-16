package bitovi.activities;

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

			String prompt = String.format(
					"""
							Here are guidelines that all responses should follow:

							<document>
							Purpose:
							To ensure all customer service agents maintain a consistently high standard of professionalism, friendliness, and efficiency in every customer interaction.

							Policies:
							1. Greet Every Customer Warmly
							- Policy: Begin every conversation with a personalized, friendly greeting using the customer’s name when available.
							- Standard: Greetings must be included in every initial response, ideally within 1 minute of receiving the ticket or chat.

							2. Acknowledge the Customer's Concern
							- Policy: Show empathy and understanding before offering solutions.
							- Standard: Each initial response should include at least one empathetic or validating statement.

							3. Use Positive Language at All Times
							- Policy: Frame messages constructively, avoiding negative or limiting language.
							- Standard: Positive and supportive language should be used consistently throughout interactions, with a target of 90%% or more of all communications.

							4. Provide Clear and Concise Answers
							- Policy: Ensure responses are easy to understand and free of jargon.
							- Standard: Keep messages brief and to the point—generally no more than 3 sentences per section unless additional detail is required.

							5. Respond Within a Timely Manner
							- Policy: Acknowledge and respond to inquiries promptly.
							- Standard: Live chat responses should be sent within 5 minutes.

							6. Follow Up on Unresolved Issues
							- Policy: Proactively update customers on pending issues.
							- Standard: Provide updates every 24 hours for any unresolved issue until it is fully addressed.

							7. Use the Customer’s Name Whenever Appropriate
							- Policy: Personalize messages by including the customer’s name.
							- Standard: The customer's name should be used at least once per interaction, ideally at the beginning or end.

							8. Verify Resolution Satisfaction Before Closing
							- Policy: Ensure the customer confirms the issue is resolved before closing a case.
							- Standard: Request confirmation of resolution in every closing message.

							9. Document Every Interaction Clearly
							- Policy: Record key conversation details and next steps in the internal ticketing system.
							- Standard: Internal notes must be added to 100%% of interactions before a case is closed or transferred.

							10. End Every Interaction on a Positive Note
							- Policy: Close with appreciation and a friendly farewell.
							- Standard: Each final message should include a thank you and a warm closing such as 'Have a great day!'
							</document>

							Provide a detailed explanation for your grade.
							Please list **all** Policies and whether or not they are being followed.
							Follow this format:

							<formatting_example>
							Overall Score: 3
							Explanation: The agent's response provides accurate information but fails to meet several key customer service policies.
							Policies:
							1. Greet Every Customer Warmly ❌
							2. Acknowledge the Customer's Concern ❌
							3. Use Positive Language at All Times ✅
							4. Provide Clear and Concise Answers ✅
							5. Respond Within a Timely Manner ✅
							6. Follow Up on Unresolved Issues ✅
							7. Use the Customer’s Name Whenever Appropriate ❌
							8. Verify Resolution Satisfaction Before Closing ✅
							9. Document Every Interaction Clearly ❓
							10. End Every Interaction on a Positive Note ✅
							</formatting_example>

							Evaluate the following interaction based on the guidelines and assign a score from 1-5 (where 1 = poor, 5 = excellent).

							<user_question>
							%s
							</user_question>

							<customer_service_agent_response>
							%s
							</customer_service_agent_response>
							""",
					userQuestion, agentResponse);

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
					.system(systemContent)
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
