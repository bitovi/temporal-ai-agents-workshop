package bitovi.common.aws;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import bitovi.common.Config;
import bitovi.workflow.types.ContextEntry;
import io.temporal.activity.Activity;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.SearchCriteria;
import software.amazon.awssdk.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.Memory;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public class AgentCoreMemory {

    private static Config config = new Config();

    private static String USER_ACTOR_ID = config.getProperty("USER_ACTOR_ID");

    private static String MEMORY_ID = "memory_dy2bk-G5fQBo4USF";

    private static String SESSION_ID = "agent-workflow-ca19bc68-5bd9-457d-b81a-52931370dce7";

    public static Memory getMemory() {
        try (BedrockAgentCoreControlClient bedrockAgentCoreControlClient = AWS
                .getBedrockAgentCoreControlClient()) {

            GetMemoryRequest getRequest = GetMemoryRequest.builder()
                    .memoryId(MEMORY_ID)
                    .build();

            Memory memory = bedrockAgentCoreControlClient.getMemory(getRequest).memory();

            System.out.println("Memory found: " + memory.name());
            return memory;
        }
    }

    public static MemoryStrategyType getMemoryStrategyType(MemoryRecordSummary summary) {
        return getMemory().strategies().stream()
            .filter(strat -> strat.strategyId().equals(summary.memoryStrategyId()))
            .findFirst()
            .orElseThrow()
            .type();
    }

    public static void createEvent(List<ContextEntry> entries) {

        try (BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient()) {
            // Create a Conversational payload for each ContextEntry
            List<PayloadType> payloads = entries.stream()
                .map(entry -> {
                    Conversational conversational = Conversational.builder()
                        .content(Content.fromText(entry.toXmlString()))
                        .role(entry.role())
                        .build();
                    return PayloadType.builder().conversational(conversational).build();
                })
                .collect(Collectors.toList());

            // Use timestamp from first entry
            Instant eventTimestamp = entries.isEmpty() ? Instant.now() : entries.get(0).timestamp();

            CreateEventRequest request = CreateEventRequest.builder()
                    .memoryId(MEMORY_ID)
                    .sessionId(Activity.getExecutionContext().getInfo().getWorkflowId())
                    .actorId(USER_ACTOR_ID)
                    .payload(payloads)
                    .eventTimestamp(eventTimestamp)
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

    public static ListEventsResponse listEvents() {
        try (BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient()) {

            ListEventsRequest request = ListEventsRequest.builder()
                .memoryId(MEMORY_ID)
                .actorId(USER_ACTOR_ID)
                .sessionId(SESSION_ID)
                .build();

            ListEventsResponse response = bedrockAgentCoreClient.listEvents(request);
            return response;
        }
    }


    public static ListMemoryRecordsResponse listMemoryRecords() {
        try (BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient()) {


            ListMemoryRecordsRequest request = ListMemoryRecordsRequest.builder()
                .memoryId(MEMORY_ID)
                .namespace("/")
                .build();


            ListMemoryRecordsResponse response = bedrockAgentCoreClient.listMemoryRecords(request);
            return response;
        }
    }

    public static RetrieveMemoryRecordsResponse retrieveMemoryRecords(String query, MemoryStrategyType strategyType) {
        try (BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient()) {
            var memoryStrategyId = getMemory().strategies().stream()
                .filter(strat -> strat.type() == strategyType)
                .findFirst()
                .orElseThrow()
                .strategyId();

            SearchCriteria searchCriteria = SearchCriteria.builder()
                    .memoryStrategyId(memoryStrategyId)
                    .searchQuery(query)
                    .build();

            RetrieveMemoryRecordsRequest retrieveMemoryRecordsRequest = RetrieveMemoryRecordsRequest.builder()
                    .memoryId(MEMORY_ID)
                    .maxResults(4)
                    .namespace("/strategies/" + memoryStrategyId + "/actors/" + USER_ACTOR_ID)
                    .searchCriteria(searchCriteria)
                    .build();

            RetrieveMemoryRecordsResponse response = bedrockAgentCoreClient.retrieveMemoryRecords(retrieveMemoryRecordsRequest);
            return response;
        }

    }
}
