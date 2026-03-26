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
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateMemoryResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.DeleteMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.DeleteMemoryResponse;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.EpisodicMemoryStrategyInput;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.EpisodicReflectionConfigurationInput;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.GetMemoryRequest;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.Memory;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyInput;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.SemanticMemoryStrategyInput;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.SummaryMemoryStrategyInput;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.UserPreferenceMemoryStrategyInput;

public class AgentCoreMemory {
        private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AgentCoreMemory.class);

        private static final Config config = new Config();
        private static final String USER_ID = config.getProperty("USER_ID");
        private static final String MEMORY_ID = config.getProperty("AWS_BEDROCK_AGENTCORE_MEMORY_ID");

        public static Memory getMemory() {
                try (BedrockAgentCoreControlClient bedrockAgentCoreControlClient = AWS
                                .getBedrockAgentCoreControlClient()) {

                        GetMemoryRequest getRequest = GetMemoryRequest.builder()
                                        .memoryId(MEMORY_ID)
                                        .build();

                        Memory memory = bedrockAgentCoreControlClient.getMemory(getRequest).memory();
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
                                                                .content(Content.fromText(entry.toXMLString()))
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
                                        .actorId(USER_ID)
                                        .payload(payloads)
                                        .eventTimestamp(eventTimestamp)
                                        .build();

                        CreateEventResponse reponse = bedrockAgentCoreClient.createEvent(request);

                        reponse.event().payload().forEach(payloadType -> {
                                if (payloadType.conversational() != null) {
                                        logger.info("Stored conversational content in memory: "
                                                        + payloadType.conversational().content().text());
                                }
                        });
                }
        }

        public static ListEventsResponse listEvents(String sessionId) {
                try (BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient()) {

                        ListEventsRequest request = ListEventsRequest.builder()
                                        .memoryId(MEMORY_ID)
                                        .actorId(USER_ID)
                                        .sessionId(sessionId)
                                        .build();

                        ListEventsResponse response = bedrockAgentCoreClient.listEvents(request);
                        return response;
                }
        }

        public static ListMemoryRecordsResponse listMemoryRecords(List<MemoryStrategyType> strategyTypes) {
                try (BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient()) {
                        List<MemoryRecordSummary> allRecords = new java.util.ArrayList<>();

                        for (var strategy : getMemory().strategies()) {
                                if (!strategyTypes.contains(strategy.type()))
                                        continue;
                                String namespace = "/strategies/" + strategy.strategyId() + "/actors/" + USER_ID;

                                ListMemoryRecordsRequest request = ListMemoryRecordsRequest.builder()
                                                .memoryId(MEMORY_ID)
                                                .namespace(namespace)
                                                .build();

                                ListMemoryRecordsResponse response = bedrockAgentCoreClient.listMemoryRecords(request);
                                allRecords.addAll(response.memoryRecordSummaries());
                        }

                        return ListMemoryRecordsResponse.builder()
                                        .memoryRecordSummaries(allRecords)
                                        .build();
                }
        }

        public static RetrieveMemoryRecordsResponse retrieveMemoryRecords(String query,
                        List<MemoryStrategyType> strategyTypes) {
                if (query.length() > 1000) {
                        logger.warn("Search query exceeds 1000 characters, truncating to 1000 characters");
                        query = query.substring(0, 999);
                }

                try (BedrockAgentCoreClient bedrockAgentCoreClient = AWS.getBedrockAgentCoreClient()) {
                        List<MemoryRecordSummary> allRecords = new java.util.ArrayList<>();

                        for (MemoryStrategyType strategyType : strategyTypes) {
                                var memoryStrategyId = getMemory().strategies().stream()
                                                .filter(strat -> strat.type() == strategyType)
                                                .findFirst()
                                                .orElseThrow()
                                                .strategyId();

                                SearchCriteria searchCriteria = SearchCriteria.builder()
                                                .memoryStrategyId(memoryStrategyId)
                                                .searchQuery(query)
                                                .build();

                                RetrieveMemoryRecordsRequest retrieveMemoryRecordsRequest = RetrieveMemoryRecordsRequest
                                                .builder()
                                                .memoryId(MEMORY_ID)
                                                .maxResults(10)
                                                .namespace("/strategies/" + memoryStrategyId + "/actors/" + USER_ID)
                                                .searchCriteria(searchCriteria)
                                                .build();

                                RetrieveMemoryRecordsResponse response = bedrockAgentCoreClient
                                                .retrieveMemoryRecords(retrieveMemoryRecordsRequest);
                                allRecords.addAll(response.memoryRecordSummaries());
                        }

                        return RetrieveMemoryRecordsResponse.builder()
                                        .memoryRecordSummaries(allRecords)
                                        .build();
                }
        }

        public static CreateMemoryResponse createMemory() {
                try (BedrockAgentCoreControlClient controlClient = AWS.getBedrockAgentCoreControlClient()) {

                        CreateMemoryRequest request = CreateMemoryRequest.builder()
                                        .name(MEMORY_ID)
                                        .description(
                                                        "This is a temporary resource for the Temporal AI Agents Workshop (Part 2) delivered by Bitovi.")
                                        .eventExpiryDuration(30) // Events expire after 30 days
                                        .memoryStrategies(
                                                        MemoryStrategyInput.builder()
                                                                        .episodicMemoryStrategy(
                                                                                        EpisodicMemoryStrategyInput
                                                                                                        .builder()
                                                                                                        .name("Episodic")
                                                                                                        .description("Stores temporal sequences of events")
                                                                                                        .namespaces(List.of(
                                                                                                                        "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}"))
                                                                                                        .reflectionConfiguration(
                                                                                                                        EpisodicReflectionConfigurationInput
                                                                                                                                        .builder()
                                                                                                                                        .namespaces(
                                                                                                                                                        List.of("/strategies/{memoryStrategyId}/actors/{actorId}"))
                                                                                                                                        .build())
                                                                                                        .build())
                                                                        .build(),
                                                        MemoryStrategyInput.builder()
                                                                        .userPreferenceMemoryStrategy(
                                                                                        UserPreferenceMemoryStrategyInput
                                                                                                        .builder()
                                                                                                        .name("Preference")
                                                                                                        .description("Tracks user preferences and choices")
                                                                                                        .namespaces(List.of(
                                                                                                                        "/strategies/{memoryStrategyId}/actors/{actorId}"))
                                                                                                        .build())
                                                                        .build(),
                                                        MemoryStrategyInput.builder()
                                                                        .semanticMemoryStrategy(
                                                                                        SemanticMemoryStrategyInput
                                                                                                        .builder()
                                                                                                        .name("Semantic")
                                                                                                        .description("Stores factual information and concepts")
                                                                                                        .namespaces(List.of(
                                                                                                                        "/strategies/{memoryStrategyId}/actors/{actorId}"))
                                                                                                        .build())
                                                                        .build(),
                                                        MemoryStrategyInput.builder()
                                                                        .summaryMemoryStrategy(
                                                                                        SummaryMemoryStrategyInput
                                                                                                        .builder()
                                                                                                        .name("Summary")
                                                                                                        .description("Maintains summarized conversation history")
                                                                                                        .namespaces(List.of(
                                                                                                                        "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}"))
                                                                                                        .build())
                                                                        .build())
                                        .build();

                        logger.info("Creating memory with request:");
                        logger.info("  Name: " + request.name());
                        logger.info("  Description: " + request.description());
                        logger.info("  Event Expiry Duration: " + request.eventExpiryDuration() + " days");
                        logger.info("  Number of strategies: " + request.memoryStrategies().size());

                        CreateMemoryResponse response = controlClient.createMemory(request);
                        return response;
                } catch (software.amazon.awssdk.services.bedrockagentcorecontrol.model.ValidationException e) {
                        logger.error("\n=== Validation Error Creating Memory ===");
                        logger.error("Error Message: " + e.getMessage());
                        logger.error("Status Code: " + e.statusCode());
                        logger.error("Request ID: " + e.requestId());
                        logger.error("Service: " + e.awsErrorDetails().serviceName());
                        logger.error("Error Code: " + e.awsErrorDetails().errorCode());
                        logger.error("Error Message (detailed): " + e.awsErrorDetails().errorMessage());
                        if (e.awsErrorDetails().sdkHttpResponse() != null) {
                                logger.error(
                                                "HTTP Status: " + e.awsErrorDetails().sdkHttpResponse().statusCode());
                        }
                        logger.error("======================================\n");
                        throw e;
                } catch (Exception e) {
                        logger.error("Unexpected error creating memory: " + e.getMessage(), e);
                        throw e;
                }
        }

        public static DeleteMemoryResponse deleteMemory(String memoryId) {
                try (BedrockAgentCoreControlClient controlClient = AWS.getBedrockAgentCoreControlClient()) {

                        logger.info("Deleting memory with ID: " + memoryId);

                        DeleteMemoryRequest request = DeleteMemoryRequest.builder()
                                        .memoryId(memoryId)
                                        .build();

                        DeleteMemoryResponse response = controlClient.deleteMemory(request);
                        return response;
                } catch (software.amazon.awssdk.services.bedrockagentcorecontrol.model.ResourceNotFoundException e) {
                        logger.error("\n=== Resource Not Found Error Deleting Memory ===");
                        logger.error("Memory ID: " + memoryId);
                        logger.error("Error Message: " + e.getMessage());
                        logger.error("Status Code: " + e.statusCode());
                        logger.error("Request ID: " + e.requestId());
                        logger.error("Service: " + e.awsErrorDetails().serviceName());
                        logger.error("Error Code: " + e.awsErrorDetails().errorCode());
                        logger.error("================================================\n");
                        throw e;
                } catch (software.amazon.awssdk.services.bedrockagentcorecontrol.model.ValidationException e) {
                        logger.error("\n=== Validation Error Deleting Memory ===");
                        logger.error("Memory ID: " + memoryId);
                        logger.error("Error Message: " + e.getMessage());
                        logger.error("Status Code: " + e.statusCode());
                        logger.error("Request ID: " + e.requestId());
                        logger.error("Service: " + e.awsErrorDetails().serviceName());
                        logger.error("Error Code: " + e.awsErrorDetails().errorCode());
                        logger.error("Error Message (detailed): " + e.awsErrorDetails().errorMessage());
                        logger.error("============================================\n");
                        throw e;
                } catch (Exception e) {
                        throw e;
                }
        }
}
