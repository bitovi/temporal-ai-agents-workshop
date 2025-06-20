package bitovi.workflows.AgentGoal.activities;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import bitovi.common.Config;
import bitovi.common.DataTypes.EnvLookupInputRecord;
import bitovi.common.DataTypes.EnvLookupOutputRecord;
import bitovi.common.DataTypes.MCPServerDefinitionRecord;
import bitovi.common.DataTypes.ValidationInputRecord;
import bitovi.common.DataTypes.ValidationResultRecord;
import bitovi.common.LLMProviderException;
import bitovi.workflows.AgentGoal.helpers.AgentToolPlannerResult;
import io.temporal.activity.Activity;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

public class AgentGoalsActivitiesImpl implements AgentGoalActivities {
    protected String AWS_MODEL_ID;
    protected String AWS_MODEL_ARN;

    @Override
    public ValidationResultRecord validateUserInput(ValidationInputRecord input) {
        return new ValidationResultRecord(true, "");
    }

    @Override
    public EnvLookupOutputRecord getWorkflowEnvSettings(EnvLookupInputRecord input) {
        EnvLookupOutputRecord output = new EnvLookupOutputRecord(true, true);
        return output;
    }

    @Override
    public ListModelContextProtocolToolsResult listModelContextProtocolTools(
            MCPServerDefinitionRecord mcpServerDefinition, List<String> includeTools) {
        throw new UnsupportedOperationException("Unimplemented method 'listModelContextProtocolTools'");
    }

    @Override
    public AgentToolPlannerResult agentToolPlanner(AgentToolPlannerInput input) {
        BedrockRuntimeClient brc = bedrockRuntimeClient();

        ArrayList<Message> messages = new ArrayList<Message>();
        messages.add(Message.builder().role("user").content(ContentBlock.fromText(input.prompt())).build());

        try {
            ConverseRequest request = ConverseRequest.builder()
                    .modelId(AWS_MODEL_ARN)
                    .messages(messages)
                    .system(SystemContentBlock.fromText(input.contextInstructions()))
                    .build();

            ConverseResponse response = brc.converse(request);
            if (response == null || response.output() == null || response.output().message() == null) {
                throw new LLMProviderException("No response received from Bedrock.");
            }

            String responseText = response.output().message().content().get(0).text();

            System.out.println("Response from Bedrock: " + responseText);
            if (responseText == null || responseText.isEmpty()) {
                throw new LLMProviderException("Received empty response from Bedrock.");
            }

            AgentToolPlannerResult result = AgentToolPlannerResult.from(new JSONObject(responseText));
            return result;
        } catch (LLMProviderException e) {
            System.err.println("Error during chat with BedrockProvider: " + e.getMessage());
            throw Activity.wrap(e);
        }
    }

    @Override
    public Object handleMissingArgs(String currentTool, Object args, Object toolData,
            ArrayList<String> promptQueue) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleMissingArgs'");
    }

    private BedrockRuntimeClient bedrockRuntimeClient() {
        this.AWS_MODEL_ID = Config.getProperty("AWS_MODEL_ID");
        this.AWS_MODEL_ARN = Config.getProperty("AWS_MODEL_ARN");

        String AWS_ACCESS_KEY_ID = Config.getProperty("AWS_ACCESS_KEY_ID");
        String AWS_SECRET_ACCESS_KEY = Config.getProperty("AWS_SECRET_ACCESS_KEY");
        String AWS_SESSION_TOKEN = Config.getProperty("AWS_SESSION_TOKEN");

        BedrockRuntimeClient bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(
                                AWS_ACCESS_KEY_ID,
                                AWS_SECRET_ACCESS_KEY,
                                AWS_SESSION_TOKEN)))
                .region(Region.US_EAST_2)
                .build();
        return bedrockRuntimeClient;
    }

}
