# Reasoning and Acting Code

```java
List<String> context = new ArrayList<>();
Workflow.await(() -> !pendingMsgs.isEmpty());
while (true) {
    // Process all pending messages
    if (!pendingMsgs.isEmpty()) {
        for (MessagePayload msg : pendingMsgs) {
            context.add(formatUserMessageContext(msg));
        }
        pendingMsgs.clear();
    }

    ThoughtResponse thoughts = activities.thoughtActivity(context);
    if (thoughts.type().equals("answer")) {
        return thoughts.answer();
    }

    if (thoughts.type().equals("action")) {
        context.add(formatThoughtContext(thoughts.thought()));

        ActionDetail action = thoughts.action();
        context.add(formatActionContext(action.name(), action.input()));

        String actionResult = activities.actionActivity(action.name(), action.input());
        ObservationResponse observationResponse = activities.observationActivity(context, actionResult);

        context.add(formatObservationContext(observationResponse.observations()));
    }
}
```
