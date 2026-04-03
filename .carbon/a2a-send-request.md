```java
public static String process(AgentConnection conn, Message message) {
    StringBuilder responseBuilder = new StringBuilder();
    AtomicReference<String> errorRef = new AtomicReference<>();
    AtomicReference<String> resultJsonRef = new AtomicReference<>();
    List<Map<String, Object>> collectedArtifacts = Collections.synchronizedList(new ArrayList<>());

    List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
        (event, card) -> {
            if (event instanceof TaskUpdateEvent tue) {
                UpdateEvent ue = tue.getUpdateEvent();

                if (ue instanceof TaskStatusUpdateEvent tsue) {
                    TaskState state = tsue.getStatus().state();
                    String statusMsg = A2AHelpers.extractTextFromMessage(tsue.getStatus().message());

                    if (state == TaskState.WORKING) {
                        // Nothing to do here, we just need to wait for the Remote Agent to progress
                    } else if (state == TaskState.INPUT_REQUIRED) {
                        InputRequiredEventData data = new InputRrequiredEventData(tsue.getTaskId(), tsue.getContextId(), statusMsg);
                        resultJsonRef.set(data.toJSON());
                    } else if (state == TaskState.COMPLETED) {
                        CompletedEventData data = new CompletedEventData(statusMsg, collectedArtifacts);
                        resultJsonRef.set(data.toJSON());
                    } else if (state == TaskState.FAILED) {
                        FailedEventData data = new FailedEventData(statusMsg);
                        resultJsonRef.set(data.toJSON());
                    } else {
                        UnknownEventData data = new UnknownEventData(statusMsg);
                        resultJsonRef.set(data.toJSON());
                    }

                } else if (ue instanceof TaskArtifactUpdateEvent taue) {
                    Artifact artifact = taue.getArtifact();
                    Map<String, Object> artifactMap = new HashMap<>();
                    if (artifact.parts() != null) {
                        for (Part<?> part : artifact.parts()) {
                            if (part instanceof DataPart dataPart) {
                                artifactMap.put("title", artifact.name());
                                artifactMap.put("data", dataPart.getData());
                            } else if (part instanceof TextPart textPart) {
                                artifactMap.put("title", artifact.name());
                                artifactMap.put("text", textPart.getText());
                            }
                        }
                    }
                    collectedArtifacts.add(artifactMap);
                }
            } else if (event instanceof MessageEvent messageEvent) {
                Message msg = messageEvent.getMessage();
                String text = A2AHelpers.extractTextFromParts(msg.getParts());
                if (!text.isEmpty()) {
                    responseBuilder.append(text);
                    if (resultJsonRef.get() == null) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("status", "completed");
                        result.put("message", text);
                        if (!collectedArtifacts.isEmpty()) {
                            result.put("artifacts", collectedArtifacts);
                        }
                        resultJsonRef.set(gson.toJson(result));
                    }
                }
            } else if (event instanceof TaskEvent taskEvent) {
                Task task = taskEvent.getTask();
                TaskState taskState = task.getStatus() != null ? task.getStatus().state(): TaskState.COMPLETED;
                String stateStr = taskState.name().toLowerCase().replace("task_state_", "").replace('_','-');

                if (resultJsonRef.get() == null) {
                    String text = "";
                    if (task.getStatus() != null && task.getStatus().message() != null) {
                        text = A2AHelpers.extractTextFromMessage(task.getStatus().message());
                    }
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", stateStr);
                    result.put("message", text);

                    if (taskState == TaskState.INPUT_REQUIRED) {
                        result.put("taskId", task.getId());
                        result.put("contextId", task.getContextId());
                    }

                    if (!collectedArtifacts.isEmpty()) {
                        result.put("artifacts", collectedArtifacts);
                    }
                    resultJsonRef.set(gson.toJson(result));
                }
            }
        });

        // Actually send the message with all our event consumers attached
        conn.client().sendMessage(message, consumers);

        String resultJson = resultJsonRef.get();
        if (resultJson != null) {
            return resultJson;
        }

        String error = errorRef.get();
        if (error != null) {
            return gson.toJson(Map.of("status", "failed", "message", "Error: " + error));
        }

        String text = responseBuilder.toString();
        if (!text.isEmpty()) {
            return gson.toJson(Map.of("status", "completed", "message", text));
        }

        return gson.toJson(Map.of("status", "failed", "message", "No response received from " + conn.card().name()));
    }
```

## TaskUpdateEvent Handler

```java
public static String process(AgentConnection conn, Message message) {
    AtomicReference<String> errorRef = new AtomicReference<>();
    AtomicReference<String> resultJsonRef = new AtomicReference<>();
    List<Map<String, Object>> collectedArtifacts = Collections.synchronizedList(new ArrayList<>());

    List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of(
        (event, card) -> {
            if (event instanceof TaskUpdateEvent tue) {
                UpdateEvent ue = tue.getUpdateEvent();
                if (ue instanceof TaskStatusUpdateEvent tsue) {
                    // Handle status updates (working, input required, completed, failed, etc.)
                } else if (ue instanceof TaskArtifactUpdateEvent taue) {
                    // Handle new artifacts produced by the task
                }
            } else if (event instanceof TaskEvent taskEvent) {
                // Handle TaskEvents, provides the final Task when complete
            }
        });

    // Send the message, wait until we need to do something, then build the result and return
    conn.client().sendMessage(message, consumers);
    latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return buildResult(errorRef, resultJsonRef, collectedArtifacts);
}
```

### TaskStatusUpdateEvent Handler

```java
public static String process(AgentConnection conn, Message message) {
    // Result collection objects defined here...
    List<BiConsumer<ClientEvent, AgentCard>> consumers = List.of((event, card) -> {
        UpdateEvent ue = tue.getUpdateEvent();
        if (ue instanceof TaskStatusUpdateEvent tsue) {
            String statusMsg = A2AHelpers.extractTextFromMessage(tsue);
            if (tsue.getStatus() == TaskState.WORKING) {
                // Nothing to do here, we just need to wait for the Remote Agent to progress
            } else if (tsue.getStatus() == TaskState.INPUT_REQUIRED) {
                InputRequiredEventData data = new InputRrequiredEventData(tsue.getTaskId(), tsue.getContextId(), statusMsg);
                resultJsonRef.set(data.toJSON());
                latch.countDown();
            } else {
                // Use this for COMPLETED, FAILED, and UNKNOWN states, as they all
                // indicate the Remote Agent is done and we should return a final result
                FinalEventData data = new FinalEventData(statusMsg);
                resultJsonRef.set(data.toJSON());
                latch.countDown();
            }
        }
    });

    // Send the message, wait until we need to do something, then build the result and return
    conn.client().sendMessage(message, consumers);
    latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    return buildResult(errorRef, resultJsonRef, collectedArtifacts);
}
```

## TaskArtifactUpdateEvent Handler

```java
List<Map<String, Object>> collectedArtifacts = Collections.synchronizedList(new ArrayList<>());

if (ue instanceof TaskArtifactUpdateEvent taue) {
    Artifact artifact = taue.getArtifact();
    Map<String, Object> artifactMap = new HashMap<>();
    if (artifact.parts() != null) {
        for (Part<?> part : artifact.parts()) {
            if (part instanceof DataPart dataPart) {
                artifactMap.put("title", artifact.name());
                artifactMap.put("data", dataPart.getData());
            } else if (part instanceof TextPart textPart) {
                artifactMap.put("title", artifact.name());
                artifactMap.put("text", textPart.getText());
            }
        }
    }
    collectedArtifacts.add(artifactMap);
}

```

## Build Result Method

```java
public static String buildResult(AtomicReference<String> errorRef, AtomicReference<String> resultJsonRef, List<Map<String, Object>> collectedArtifacts) {
    String resultJson = resultJsonRef.get();
    if (resultJson != null) {
        return gson.toJson(Map.of("status", "success", "message", resultJson, "artifacts", collectedArtifacts));
    }

    String error = errorRef.get();
    if (error != null) {
        return gson.toJson(Map.of("status", "failed", "message", "Error: " + error));
    }

    return gson.toJson(Map.of("status", "failed", "message", "No response received from agent"));
}
```
