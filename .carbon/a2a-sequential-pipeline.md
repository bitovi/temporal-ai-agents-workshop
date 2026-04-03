# Sequential Agent Pipeline

## Code example

```java
LlmAgent writer = LlmAgent.builder()
    .name("CodeWriter")
    .instruction("Write Java code to fulfill the given requirements")
    .outputKey("java_code")
    .build();

LlmAgent reviewer = LlmAgent.builder()
    .name("CodeReviewer")
    .instruction("Review the Java code in {java_code} and ensure it meets the requirements")
    .outputKey("review")
    .build();

LlmAgent refactor = LlmAgent.builder()
    .name("RefactorWriter")
    .instruction("Report the result from {review} and if there are issues, refactor the code in {java_code} to address them")
    .build();

SequentialAgent javaPipeline = SequentialAgent.builder()
    .name("JavaPipeline")
    .subAgents(writer, reviewer, refactor)
    .build();
```
