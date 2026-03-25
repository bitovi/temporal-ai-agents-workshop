package bitovi.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

import io.reactivex.rxjava3.core.Flowable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

public final class BedrockModel extends BaseLlm {
    private static final Config config = new Config();
    private final AwsCredentialsProvider provider;
    private final BedrockRuntimeClient client;
    private final Region region;

    private static final Logger logger = LoggerFactory.getLogger(BedrockModel.class);

    public BedrockModel(String model) {
        super(model);
        this.provider = getDefaultAwsCredentialsProvider();
        this.client = getBedrockRuntimeClient();
        this.region = getAwsRegion();

    }

    public BedrockModel(String model, AwsCredentialsProvider provider) {
        super(model);
        this.provider = provider;
        this.client = getBedrockRuntimeClient();
        this.region = getAwsRegion();
    }

    @Override
    public Flowable<LlmResponse> generateContent(LlmRequest llmRequest, boolean stream) {
        if (stream) {
            return generateStreamingContent(llmRequest);
        } else {
            return generateDefaultContent(llmRequest);
        }
    }

    private Flowable<LlmResponse> generateStreamingContent(LlmRequest llmRequest) {
        logger.info("Generating streaming content for request");
        throw new UnsupportedOperationException("Unimplemented method 'generateStreamingContent'");
    }

    private Flowable<LlmResponse> generateDefaultContent(LlmRequest llmRequest) {
        logger.info("Generating default content for request");
        Optional<String> optionalSystem = llmRequest.getFirstSystemInstruction();
        String system;
        if (optionalSystem.isEmpty()) {
            logger.debug("Setting default system prompt");
            system = "You are a helpful assistant.";
        } else {
            logger.debug("Using system prompt: {}", optionalSystem.get());
            system = optionalSystem.get();
        }

        logger.info("Preparing messages for request");
        List<BedrockMessage> messages = llmRequest.contents().stream()
                .map(content -> {
                    Optional<String> _role = content.role();
                    if (_role.isEmpty()) {
                        logger.debug("Setting default role 'user' for message: {}", content.text());
                        return new BedrockMessage("user", content.text());
                    } else {
                        if (_role.get().equals("user")) {
                            logger.debug("Using role 'user' for message: {}", content.text());
                            return new BedrockMessage("user", content.text());
                        } else {
                            logger.debug("Using role 'assistant' for message: {}", content.text());
                            return new BedrockMessage("assistant", content.text());
                        }
                    }
                })
                .collect(Collectors.toList());

        logger.info("Preparing tools for request");
        List<Tool> tools = new ArrayList<>();
        llmRequest.tools().forEach(((string, baseTool) -> {
            logger.info("Preparing tool: {} with description: {}", string, baseTool.description());
        }));

        ConverseResponse result = bedrockCompletionRequest(system, messages, tools, this.model());

        logger.info("Processing response from Bedrock client: {}", result);
        List<Part> textParts = new ArrayList<>();

        List<ContentBlock> contentBlocks = result.output().message().content();
        for (ContentBlock block : contentBlocks) {
            if (block.text() != null && !block.text().isEmpty()) {
                logger.info("Processing content block: {}", block.text());
                Part p = Part.builder().text(block.text()).build();
                textParts.add(p);
            }

            if (block.reasoningContent() != null && !block.reasoningContent().reasoningText().text().isEmpty()) {
                logger.info("Processing reasoning content block: {}", block.reasoningContent());
                Part p = Part.builder().thought(true).text(block.reasoningContent().reasoningText().text()).build();
                textParts.add(p);
            }
        }

        LlmResponse llmResponse = LlmResponse.builder().content(Content.builder().parts(textParts).build()).build();
        logger.info("Generated LLM response: {}", llmResponse);
        return Flowable.just(llmResponse);

    }

    private ConverseResponse bedrockCompletionRequest(String system, List<BedrockMessage> messages, List<Tool> tools,
            String modelId) {

        List<Message> bedrockMessages = new ArrayList<>();
        for (BedrockMessage m : messages) {
            logger.info("Preparing message with role: {} and content: {}", m.role(), m.content());
            bedrockMessages.add(
                    Message.builder()
                            .role(ConversationRole.fromValue(m.role()))
                            .content(ContentBlock.fromText(m.content()))
                            .build());
        }

        logger.info("Prepared {} messages for request", bedrockMessages.size());

        ConverseRequest request;
        if (tools != null && !tools.isEmpty()) {
            ToolConfiguration tc = ToolConfiguration.builder().tools(tools).build();
            logger.info("Prepared tool configuration with {} tools for request", tools.size());

            request = ConverseRequest.builder()
                    .modelId(modelId)
                    .messages(bedrockMessages)
                    .system(SystemContentBlock.fromText(system.trim()))
                    .toolConfig(tc)
                    .build();
        } else {
            logger.info("Preparing ConverseRequest without tools for request");
            request = ConverseRequest.builder()
                    .modelId(modelId)
                    .messages(bedrockMessages)
                    .system(SystemContentBlock.fromText(system.trim()))
                    .build();
        }

        logger.info("Sending ConverseRequest to Bedrock client");
        ConverseResponse response = client.converse(request);
        logger.info("Received response from Bedrock client");
        return response;
    }

    @Override
    public BaseLlmConnection connect(LlmRequest llmRequest) {
        logger.info("Connecting to LLM with request: {}", llmRequest);
        throw new UnsupportedOperationException("Unimplemented method 'connect'");
    }

    private static AwsCredentialsProvider getDefaultAwsCredentialsProvider() {
        logger.info("Getting default AWS credentials provider");
        String AWS_ACCESS_KEY_ID = config.getProperty("AWS_ACCESS_KEY_ID");
        String AWS_SECRET_ACCESS_KEY = config.getProperty("AWS_SECRET_ACCESS_KEY");
        String AWS_SESSION_TOKEN = config.getProperty("AWS_SESSION_TOKEN");

        StaticCredentialsProvider credentialsProvider;

        if (AWS_SESSION_TOKEN == null || AWS_SESSION_TOKEN.isEmpty()) {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                            AWS_ACCESS_KEY_ID,
                            AWS_SECRET_ACCESS_KEY));
        } else {
            credentialsProvider = StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(
                            AWS_ACCESS_KEY_ID,
                            AWS_SECRET_ACCESS_KEY,
                            AWS_SESSION_TOKEN));
        }
        return credentialsProvider;
    }

    private BedrockRuntimeClient getBedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(this.provider)
                .region(this.region)
                .build();
    }

    public Region getAwsRegion() {
        return Region.of(config.getProperty("AWS_REGION"));
    }

    private record BedrockMessage(String role, String content) {
    }
}
