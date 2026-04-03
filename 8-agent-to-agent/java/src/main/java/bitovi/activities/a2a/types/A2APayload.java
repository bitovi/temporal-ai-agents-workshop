package bitovi.activities.a2a.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import bitovi.activities.a2a.A2ARegistry;
import bitovi.activities.a2a.A2ARegistry.AgentConnection;
import io.a2a.A2A;
import io.a2a.spec.Message;

public record A2APayload(
        A2ARequestInput params,
        AgentConnection conn,
        Message message,
        StringBuilder responseBuilder,
        CountDownLatch latch,
        AtomicReference<String> errorRef,
        AtomicReference<String> resultJsonRef,
        List<Map<String, Object>> collectedArtifacts,
        String agentName) {

    public static A2APayload fromParams(A2ARequestInput params) throws Exception {
        AgentConnection conn = A2ARegistry.getOrCreateConnection(params.agentUrl());

        Message message;
        if (params.existingTask()) {
            message = A2A.createUserTextMessage(params.message(),
                    params.contextId(),
                    params.taskId());
        } else {
            message = A2A.toUserMessage(params.message());
        }

        StringBuilder responseBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<String> resultJsonRef = new AtomicReference<>();
        List<Map<String, Object>> collectedArtifacts = new ArrayList<>();

        return new A2APayload(params, conn, message, responseBuilder, latch, errorRef, resultJsonRef,
                collectedArtifacts, conn.card().name());
    }

    public Consumer<Throwable> getErrorHandler() {
        return error -> {
            if (error == null) {
                return;
            }
            System.err.println("[A2ATool:" + this.agentName() + "] Error: " + error.getMessage());
            this.errorRef().set(error.getMessage());
            this.latch().countDown();
        };
    }
}