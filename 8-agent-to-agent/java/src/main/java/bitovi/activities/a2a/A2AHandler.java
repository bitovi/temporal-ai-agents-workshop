package bitovi.activities.a2a;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import com.google.gson.Gson;

import bitovi.activities.a2a.events.ExecuteMessageEvent;
import bitovi.activities.a2a.events.ExecuteTaskEvent;
import bitovi.activities.a2a.events.ExecuteTaskUpdateEvent;
import bitovi.activities.a2a.types.A2APayload;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.spec.AgentCard;

public class A2AHandler {
    private static final int TIMEOUT_SECONDS = 120;
    private static final Gson gson = new Gson();

    public static String execute(A2APayload payload) throws InterruptedException {
        String agentName = payload.agentName();
        System.out.println("[A2ATool:" + agentName + "] Executing message...");
        List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
                (event, card) -> {
                    try {
                        switch (event) {
                            case TaskUpdateEvent tue -> ExecuteTaskUpdateEvent.process(payload, tue);
                            case MessageEvent messageEvent -> ExecuteMessageEvent.process(payload, messageEvent);
                            case TaskEvent taskEvent -> ExecuteTaskEvent.process(payload, taskEvent);
                            default -> {
                            }
                        }
                    } catch (Exception e) {
                        System.err.println(
                                "[A2ATool:" + agentName + "] Error processing event: " + e.getMessage());
                        payload.errorRef().set(e.getMessage());
                        payload.latch().countDown();
                    }
                });

        // Send the message to the agent with the appropriate event handlers attached
        A2AHandler.sendMessage(payload, consumers);

        boolean completed = payload.latch().await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            String timeoutMsg = "Request timed out after " + TIMEOUT_SECONDS + " seconds.";
            System.err.println("[A2ATool:" + agentName + "] " + timeoutMsg);
            return gson.toJson(Map.of("status", "failed", "message", timeoutMsg));
        }

        return A2AHandler.extractResult(payload);
    }

    private static void sendMessage(A2APayload payload, List<BiConsumer<ClientEvent, AgentCard>> consumers) {
        System.out.println("[A2ATool:" + payload.agentName() + "] Sending message...");
        payload.conn().client().sendMessage(payload.message(), consumers, payload.getErrorHandler());
    }

    private static String extractResult(A2APayload payload) {
        // Check resultJsonRef FIRST — the event callbacks set it on terminal
        // states (COMPLETED, FAILED, INPUT_REQUIRED). A transport-level error
        // like "Request cancelled" can arrive after the result is already
        // captured, so a valid result always takes priority over errorRef.
        String resultJson = payload.resultJsonRef().get();
        if (resultJson != null) {
            System.out.println("[A2ATool:" + payload.agentName() + "] Result: " + resultJson);
            return resultJson;
        }

        String error = payload.errorRef().get();
        if (error != null) {
            return gson.toJson(Map.of("status", "failed", "message", "Error: " + error));
        }

        String text = payload.responseBuilder().toString();
        if (!text.isEmpty()) {
            return gson.toJson(Map.of("status", "completed", "message", text));
        }

        return gson.toJson(Map.of("status", "failed", "message", "No response received from " + payload.agentName()));
    }
}
