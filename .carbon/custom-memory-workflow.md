## Custom Memory Implementation Workflow

```ts
export const memoryWorkflowEventSignal = defineSignal<[MemoryEventInput]>("memoryWorkflowEventSignal");

type MemoryExtractionWorkflowInput = {
    userId: string;
    sessionId: string;
}

const { persistMemoryEvents, memoryExtractionWorkflowConfig } = proxyActivities<typeof activities>({
    startToCloseTimeout: "10 minutes"
});

export async function memoryExtractionWorkflow(input: MemoryExtractionWorkflowInput): Promise<void> {
    const events: MemoryEventInput[] = [];

    setHandler(memoryWorkflowEventSignal, (event: MemoryEventInput) => {
        events.push(event);
    });

    while (true) {
        // Wait for an event to be signaled, or for 4 hours to pass (whichever comes first)
        const hasEvents = await condition(() => events.length > 0, "4 hours");
        if (!hasEvents) {
            // No events were signaled within 4 hours, but we have some events in memory, so we persist them and end the workflow
            if (events.length > 0) {
                await persistMemoryEvents({ sessionId: input.sessionId, userId: input.userId, events });
            }

            // No new events were signaled, we can safely end the workflow and the current session
            return;
        }
    }
}
```

## Persist Memory Events Activity

```ts
export async function persistMemoryEvents(input: PersistMemoryEventsInput): Promise<void> {
    // 1. Search the MemoryDataStore for existing memory entries related to
    //    things in the events (e.g. entities, topics, etc.)
    const existing: Memory[] = await MemoryDataStore.search(input.userId, input.events);

    // 2. Call an LLM with a prompt that includes the new events and the existing memory entries
    //    and ask it to extract any new memory entries and update existing ones as needed
    const prompt = memoryExtractionPromptTemplate({
        sessionId: input.sessionId,
        userId: input.userId,
        events: input.events,
        existing,
    });

    const response = await openai.invoke(
        workflowId, [{ role: "user", content: prompt, type: "text" }], memoryExtractionTools()
    );
    
    // 3. Parse the LLM response and update the MemoryDataStore with any new or updated memory entries
    //    based on the memory add, update, and remove tool calls the LLM generated.
    response.payload.forEach((payload) => {
        Logger.info(workflowId, `Memory Extraction LLM Response Entry: ${JSON.stringify(payload)}`);
        const parsed = JSON.parse(payload.input);
        switch (payload.name) {
            case "SemanticMemory": {
                if (MemoryDataStoreValidator.validateSemanticMemoryPayload(parsed)) {
                    MemoryDataStore.add(input.userId, {
                        ...parsed,
                        type: "semantic",
                        memoryId: randomUUID(),
                    });
                }
                break;
            }
            // And of course all other tool types here as well
        }
    });
}
```