# SYSTEMS-580-4: Migrate Activities

This is a subsection of [580-0-convert-ts-to-java.md](./580-0-convert-ts-to-java.md) - see that spec for overall context.

## task
- we need to fix the thought activity in 5-agent-workflow/java/src/main/java/bitovi/activities/ActivitiesImpl.java
- right now, it is throwing an error like this:
```txt
thoughtEntity failed: A conversation must start with a user message. Try again with a conversation that starts with a user message. (Service: BedrockRuntime, Status Code: 400, Request ID: 04adc0e0-a82d-49cf-8c6b-bbaf8604a2b4)
```

## context
- we already lifted and shifted this impl from the temp-ref-code activities
- we switched from using openai sdk to the bedrock one
- I think the current error is because of a difference between the sdks intended usage and where we are passing the context.
- We need to figure out what's the best way to keep the functionality of the temp-ref-code impl, but using bedrock, and in java.