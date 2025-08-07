package bitovi;

import java.time.Duration;
import java.util.ArrayList;

import bitovi.activities.Activities;
import io.temporal.workflow.Workflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

public class RagWorkflowImpl implements RagWorkflow {
	private final ActivityOptions defaultActivityOptions = ActivityOptions
			.newBuilder()
			.setStartToCloseTimeout(Duration.ofSeconds(120))
			.setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())			
			.build();

	private final Activities activities = Workflow.newActivityStub(Activities.class, defaultActivityOptions);

	@Override
	public String execute(String searchTerm) {

		String basePrompt = """
				You are a conversational assistant named 'Aurora' that is helpful, creative, clever, and friendly.
				Your job is to assist users in a variety of tasks including answering questions, providing
				information, and engaging in casual conversation.

				You should respond in concise paragraphs, seperated by newlines, to maintain readability and clarity. Your knowledge is based on the data your model was trained on, which has a cutoff date of October, 2024. The current date is July, 2025. It is a {{dayOfWeek}}.

				Here are some documents fetched from a vector database that may be relevant to the user's query:
				""";

		StringBuilder promptBuilder = new StringBuilder(basePrompt);
		ArrayList<String> searchResults = activities.search(searchTerm);

		promptBuilder.append("<documents>\n");
		for (String result : searchResults) {
			promptBuilder.append("<chunk>\n");
			promptBuilder.append(result).append("\n");
			promptBuilder.append("</chunk>\n");
		}
		promptBuilder.append("</documents>");

		return promptBuilder.toString();
	}
}