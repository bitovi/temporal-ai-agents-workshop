package bitovi.common.aws;
import java.time.Instant;
import bitovi.common.Config;
import io.temporal.activity.Activity;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.Memory;

public class AgentCoreMemory {

    private static Config config = new Config();

    public static void createEvent(String memoryText) {
        Memory memory;
        try (BedrockAgentCoreControlClient bedrockAgentCoreControlClient = AWS
                .getBedrockAgentCoreControlClient()) {

            String MEMORY_ID = "memory_dy2bk-G5fQBo4USF";
            GetMemoryRequest getRequest = GetMemoryRequest.builder()
                    .memoryId(MEMORY_ID)
                    .build();

            memory = bedrockAgentCoreControlClient.getMemory(getRequest).memory();

            System.out.println("Memory found: " + memory.name());
        }

        try (BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient()) {
            Conversational userInput = Conversational.builder()
                    .content(Content.fromText(memoryText))
                    .role(Role.USER)
                    .build();

            PayloadType payload = PayloadType.builder().conversational(userInput).build();

            CreateEventRequest request = CreateEventRequest.builder()
                    .memoryId(memory.id())
                    .sessionId(Activity.getExecutionContext().getInfo().getWorkflowId())
                    .actorId(config.getProperty("USER_ACTOR_ID"))
                    .payload(payload)
                    .eventTimestamp(Instant.now())
                    .build();

            CreateEventResponse reponse = bedrockAgentCoreClient.createEvent(request);

            reponse.event().payload().forEach(payloadType -> {
                if (payloadType.conversational() != null) {
                    System.out.println("Stored conversational content in memory: "
                            + payloadType.conversational().content().text());
                }
            });
        }
    }
}
