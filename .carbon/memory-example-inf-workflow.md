# Example Workflow with Memory Persistence

```java
public void receiveMessage(MessagePayload payload) {
    pendingMsgs.add(payload);
}

public WorkflowResult execute(WorkflowInput input) {
    List<String> context = new ArrayList<>();
    List<String> persist = new ArrayList<>();
    Workflow.await(() -> !pendingMsgs.isEmpty());
    while (true) {
        // Process all pending messages
        if (!pendingMsgs.isEmpty()) {
            for (MessagePayload msg : pendingMsgs) {
                context.add(formatUserMessageContext(msg));
                persist.add(formatUserMessageContext(msg));
            }
            pendingMsgs.clear();
        }

        ThoughtResponse thoughts = activities.thoughtActivity(context);
        if (thoughts.type().equals("answer")) {
            persist.add(formatUserMessageContext(thoughts.answer()));
            // Persist memory entries for this turn (usually just the USER_MESSAGE and ANSWER)
            activities.persistMemoryActivity(persist);

            // Broadcast the answer to whatever client is listening (e.g. frontend, CLI, etc.)
            activities.broadcastMessageActivity(thoughts.answer());

            // Once this has been persisted, we can clear the persist buffer for the next turn
            persist.clear();
        }

        if (thoughts.type().equals("action")) {
            // Handle action (not shown in this example)
        }
    }
}
```
