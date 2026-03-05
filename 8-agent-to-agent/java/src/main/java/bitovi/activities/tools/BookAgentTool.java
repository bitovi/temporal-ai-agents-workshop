package bitovi.activities.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.http.A2ACardResolver;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfig;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

import bitovi.common.Config;

public class BookAgentTool {
    private static final String BOOK_AGENT_URL = new Config().getProperty("BOOK_AGENT_SERVER_BASE_URL");
    private static final String AGENT_CARD_URL = BOOK_AGENT_URL + "/.well-known/agent-card.json";
    private static final int TIMEOUT_SECONDS = 60;
    
    // Static singleton client - thread-safe and reused across all calls
    private static volatile Client client;
    private static volatile AgentCard agentCard;
    private static final Object lock = new Object();

    /**
     * Get or initialize the A2A client using lazy initialization.
     * The client is created once and reused for all subsequent calls.
     */
    private static Client getClient() throws IOException {
        if (client == null) {
            synchronized (lock) {
                if (client == null) {
                    try {
                        System.out.println("[BookAgentTool] Initializing A2A client for Book Agent at " + BOOK_AGENT_URL);
                        
                        // Fetch the agent card
                        agentCard = new A2ACardResolver(BOOK_AGENT_URL).getAgentCard();
                        System.out.println("[BookAgentTool] Successfully fetched agent card: " + agentCard.name());
                        
                        // Configure client
                        ClientConfig clientConfig = new ClientConfig.Builder()
                                .setAcceptedOutputModes(List.of("text"))
                                .build();
                        
                        // Create event consumers to capture responses
                        List<BiConsumer<ClientEvent, AgentCard>> consumers = new ArrayList<>();
                        
                        // Create the client with JSON-RPC transport
                        client = Client.builder(agentCard)
                                .clientConfig(clientConfig)
                                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                                .build();
                        
                        System.out.println("[BookAgentTool] A2A client initialized successfully");
                    } catch (Exception e) {
                        System.err.println("[BookAgentTool] Failed to initialize A2A client: " + e.getMessage());
                        throw new IOException("Failed to initialize Book Agent client: " + e.getMessage(), e);
                    }
                }
            }
        }
        return client;
    }

    /**
     * Build the tool description from the agent card.
     * Includes the agent's description and capabilities from the agent card.
     */
    private static String buildToolDescription(AgentCard card) {
        if (card == null) {
            return "Query a specialized book agent about books, authors, genres, and literature";
        }
        
        StringBuilder description = new StringBuilder(card.description());
        
        // Add skills information if available
        if (card.skills() != null && !card.skills().isEmpty()) {
            description.append(" Capabilities: ");
            card.skills().forEach(skill -> 
                description.append(skill.name()).append(", ")
            );
            // Remove trailing comma and space
            if (description.length() > 2) {
                description.setLength(description.length() - 2);
            }
        }
        
        return description.toString();
    }

    /**
     * Execute a query to the Book Agent via A2A protocol.
     */
    public static String execute(String toolName, Map<String, Object> toolUseInput) {
        System.out.println("[BookAgentTool] Executing with inputs: " + toolUseInput);

        // Extract the actual parameter map if it's nested under "map" key
        Map<String, Object> params = toolUseInput;
        if (toolUseInput.containsKey("map") && toolUseInput.get("map") instanceof Map) {
            params = (Map<String, Object>) toolUseInput.get("map");
        }

        if (params == null || !params.containsKey("query")) {
            throw new IllegalArgumentException("Invalid input: 'query' is required.");
        }

        String query = params.get("query").toString();
        System.out.println("[BookAgentTool] Sending query to Book Agent: " + query);

        try {
            // Get or initialize the client
            Client bookAgentClient = getClient();
            
            // Create user message
            Message message = A2A.toUserMessage(query);
            
            // Use StringBuilder to accumulate response
            StringBuilder responseBuilder = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> errorRef = new AtomicReference<>();
            
            // Create event consumers for this specific request
            List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
                (event, card) -> {
                    try {
                        if (event instanceof MessageEvent messageEvent) {
                            // Accumulate text from assistant messages
                            Message msg = messageEvent.getMessage();
                            boolean hasText = false;
                            if (msg.getParts() != null) {
                                for (Part<?> part : msg.getParts()) {
                                    if (part instanceof TextPart textPart) {
                                        responseBuilder.append(textPart.getText());
                                        System.out.println("[BookAgentTool] Received text: " + textPart.getText());
                                        hasText = true;
                                    }
                                }
                            }
                            // Count down latch after receiving any text content
                            // This ensures we don't wait for TaskEvent if agent doesn't send one
                            if (hasText) {
                                System.out.println("[BookAgentTool] Text content received, marking complete");
                                latch.countDown();
                            }
                        } else if (event instanceof TaskEvent taskEvent) {
                            // Task completed or failed
                            System.out.println("[BookAgentTool] Task event received");
                            latch.countDown();
                        } else if (event instanceof TaskUpdateEvent updateEvent) {
                            // Progress update
                            System.out.println("[BookAgentTool] Task update received");
                        }
                    } catch (Exception e) {
                        System.err.println("[BookAgentTool] Error processing event: " + e.getMessage());
                        errorRef.set(e.getMessage());
                        latch.countDown();
                    }
                }
            );
            
            // Error handler
            Consumer<Throwable> errorHandler = error -> {
                System.err.println("[BookAgentTool] Error during message exchange: " + error.getMessage());
                errorRef.set(error.getMessage());
                latch.countDown();
            };
            
            // Send message to Book Agent
            System.out.println("[BookAgentTool] Sending message to Book Agent...");
            bookAgentClient.sendMessage(message, consumers, errorHandler);
            
            // Wait for response with timeout
            boolean completed = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (!completed) {
                String timeoutMsg = "Request timed out after " + TIMEOUT_SECONDS + " seconds. " +
                                   "The Book Agent may be processing a complex query. " +
                                   "Please verify the Book Agent Server is running and responsive.";
                System.err.println("[BookAgentTool] " + timeoutMsg);
                return "{\"error\": \"" + timeoutMsg + "\"}";
            }
            
            // Check for errors
            String error = errorRef.get();
            if (error != null) {
                String errorMsg = "Error communicating with Book Agent: " + error + ". " +
                                 "Please verify the Book Agent Server is running at " + BOOK_AGENT_URL;
                System.err.println("[BookAgentTool] " + errorMsg);
                return "{\"error\": \"" + errorMsg + "\"}";
            }
            
            // Return accumulated response
            String result = responseBuilder.toString();
            if (result.isEmpty()) {
                result = "{\"error\": \"No response received from Book Agent\"}";
            }
            
            System.out.println("[BookAgentTool] Final response: " + result);
            return result;
            
        } catch (IOException e) {
            String errorMsg = "Failed to connect to Book Agent at " + BOOK_AGENT_URL + ": " + e.getMessage() + ". " +
                             "Please ensure the Book Agent Server is running (docker compose up).";
            System.err.println("[BookAgentTool] " + errorMsg);
            return "{\"error\": \"" + errorMsg + "\"}";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "Request was interrupted: " + e.getMessage();
            System.err.println("[BookAgentTool] " + errorMsg);
            return "{\"error\": \"" + errorMsg + "\"}";
        } catch (Exception e) {
            String errorMsg = "Unexpected error: " + e.getMessage();
            System.err.println("[BookAgentTool] " + errorMsg);
            e.printStackTrace();
            return "{\"error\": \"" + errorMsg + "\"}";
        }
    }

    /**
     * Get the Bedrock tool definition for the book agent.
     */
    public static Tool getBedrockTool() {
        // Define 'query' property (string, required)
        Map<String, Document> queryPropertyMap = new HashMap<>();
        queryPropertyMap.put("type", Document.fromString("string"));
        queryPropertyMap.put("description", Document.fromString("The question or query about books, authors, or literature"));

        // Create the "properties" object
        Map<String, Document> propertiesMap = new HashMap<>();
        propertiesMap.put("query", Document.fromMap(queryPropertyMap));

        // Create the "required" array
        List<Document> requiredList = new ArrayList<>();
        requiredList.add(Document.fromString("query"));

        // Create the root object
        Map<String, Document> rootMap = new HashMap<>();
        rootMap.put("type", Document.fromString("object"));
        rootMap.put("properties", Document.fromMap(propertiesMap));
        rootMap.put("required", Document.fromList(requiredList));

        // Create the Document representing the JSON schema
        Document document = Document.fromMap(rootMap);

        // Build description from agent card if available
        String description = buildToolDescription(agentCard);

        ToolSpecification specification = ToolSpecification.builder()
                .name("book_agent")
                .description(description)
                .inputSchema(ToolInputSchema.builder()
                        .json(document)
                        .build())
                .build();

        return Tool.builder()
                .toolSpec(specification)
                .build();
    }
}
