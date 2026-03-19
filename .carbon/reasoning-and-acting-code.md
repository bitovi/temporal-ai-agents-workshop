# Reasoning and Acting Code

```java
List<String> context = new ArrayList<>();
context.add(formatUserMessageContext(input));

while (true) {
    ThoughtResponse thoughts = activities.thoughtActivity(context);
    if (thoughts.type().equals("answer")) {
        return thoughts.answer();
    }

    if (thoughts.type().equals("action")) {
        context.add(formatThoughtContext(thoughts.thought()));

        ActionDetail action = thoughts.action();
        context.add(formatActionContext(action.name(), action.input()));

        String actionResult = activities.actionActivity(action.name(), action.input());

        ObservationResponse observationResponse = activities.observationActivity(
            thoughts.thought(), action.name(), action.input(), actionResult
        );

        context.add(formatObservationContext(observationResponse.observations()));
    }
}
```
