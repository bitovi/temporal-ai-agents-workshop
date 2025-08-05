package bitovi.activities;

import java.util.ArrayList;
import java.util.List;

import bitovi.activities.tools.FetchAccountInfo;
import bitovi.activities.tools.QuestionAnswered;
import bitovi.activities.tools.SearchWeb;
import bitovi.activities.tools.WeatherTool;
import bitovi.common.AWS;
import bitovi.common.AWS.ChatMessage;
import bitovi.common.AWS.ModelResponse;
import bitovi.common.AWS.ModelToolCall;
import bitovi.common.AWS.ToolResult;
import io.temporal.failure.ApplicationFailure;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

public class ActivitiesImpl implements Activities {
	@Override
	public ToolResult executeTool(ModelToolCall toolCall) throws ApplicationFailure {
		System.out.print("Executing tool: " + toolCall.toolName() + " with inputs: "
				+ toolCall.toolInputs() + "\n");
		switch (toolCall.toolName()) {
			case "get_weather": {
				return WeatherTool.execute(toolCall.toolName(), toolCall.toolInputs());
			}

			case "get_account_info": {
				return FetchAccountInfo.execute(toolCall.toolName(), toolCall.toolInputs());
			}

			case "search_web": {
				return SearchWeb.execute(toolCall.toolName(), toolCall.toolInputs());
			}

			case "complete_question_answered": {
				return QuestionAnswered.execute(toolCall.toolName(), toolCall.toolInputs());
			}

			default: {
				throw ApplicationFailure.newNonRetryableFailure(toolCall.toolName(), "UnknownToolCall");
			}
		}

	}

	@Override
	public ModelResponse thought(List<ChatMessage> history) throws ApplicationFailure {
		try {
			return AWS.bedrockConverse(history,
					"""
									You are a Reacting and Acting agent. This is the Thought step. You should output a response that reflects your thoughts based on the conversation history.
									You should output the steps that you think will be needed next in order to answer the users question. If you have reached the final answer, you should output the final answer.
									If you have no work to do, you should output 'skip me'.
							""",
					null);
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailureWithCause("Error during Bedrock Converse - Thought Step",
					"BedrockConverseException", e, e.getMessage());
		}
	}

	@Override
	public ModelResponse action(List<ChatMessage> history) throws ApplicationFailure {
		ToolConfiguration.Builder toolConfig = ToolConfiguration.builder();
		List<Tool> tools = new ArrayList<>();

		tools.add(WeatherTool.getBedrockTool());
		tools.add(FetchAccountInfo.getBedrockTool());
		tools.add(SearchWeb.getBedrockTool());

		toolConfig.tools(tools);

		try {
			return AWS.bedrockConverse(history,
					"""
									You are a Reacting and Acting agent. This is the Action step. You should call a tool that will help fetch the information
									needed based on the previous Thought step and conversation history. If you have reached the final answer, you should output the final answer.
							""",
					toolConfig.build());
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailureWithCause("Error during Bedrock Converse - Action Step",
					"BedrockConverseException", e, e.getMessage());
		}
	}

	@Override
	public ModelResponse observation(List<ChatMessage> history) throws ApplicationFailure {
		ToolConfiguration.Builder toolConfig = ToolConfiguration.builder();
		List<Tool> tools = new ArrayList<>();

		tools.add(QuestionAnswered.getBedrockTool());

		toolConfig.tools(tools);

		try {
			return AWS.bedrockConverse(history,
					"""
									You are a Reacting and Acting agent. This is the Observation step. You should output a response that reflects your observations based
									on the conversation history and the latest tool call result. If you have reached the final answer, you should use the final answer tool to exit the loop.
							""",
					toolConfig.build());
		} catch (Exception e) {
			throw ApplicationFailure.newNonRetryableFailureWithCause("Error during Bedrock Converse - Observation Step",
					"BedrockConverseException", e, e.getMessage());
		}
	}

}
