# Exercise 1 - Prompt Engineering

## Goals

Learn prompt engineering best practices and apply them to guide an LLM in evaluating a customer service agent's response against a set of guidelines.

## What you need to know

Each LLM has its own guidelines for optimizing prompts. [Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-engineering-guidelines.html) lists guides per model. This workshop uses Claude 3.7 Sonnet — see [Anthropic's guide](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview) and their [interactive tutorial](https://github.com/anthropics/prompt-eng-interactive-tutorial/tree/master/AmazonBedrock/anthropic).

### Parts of a Prompt

1. The task you want the LLM to perform
2. Context for the task
3. Examples
4. Prefilled response content

### Prompt Structure

- **Model**: the model to call
- **System message** (optional): context, instructions, and guidelines
- **Messages**: alternating `user`/`assistant` turns, starting with `user`
- **Max output tokens**: cap on generated tokens
- **Temperature**: response variability — `0` is deterministic, `1` is most variable

The structure is most explicit in the InvokeModel API:

```json
{
    "anthropic_version": "bedrock-2023-05-31",
    "system": <system prompt>,
    "messages": [
        { "role": "user", "content": [{ "type": "text", "text": <user message> }] },
        { "role": "assistant", "content": [{ "type": "text", "text": <assistant message> }] }
    ],
    "max_tokens": <max tokens>,
    "temperature": <temperature>
}
```

The newer Converse API uses a builder pattern:

```java
ConverseRequest converseRequest = ConverseRequest.builder()
  .modelId(<model>)
  .system(<system prompt>)
  .messages([<user message>, <assistant message>, ...])
  .inferenceConfig(config -> config
    .maxTokens(<max tokens>)
    .temperature(<temperature>)
    .build())
  .build();
```

### Context Windows

LLMs have context length limits. Claude's is 200k tokens (~3.5 English characters per token); a token-counting API is available. For a single request, the window includes your prompt plus the response (capped by `maxTokens`).

### Techniques

#### Be clear and direct

The LLM has no context beyond your instructions. Think of it as ["a brilliant but very new employee (with amnesia) who needs explicit instructions."](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/be-clear-and-direct)

> **Golden Rule of Clear Prompting**: Show your prompt to a colleague. If they're confused, Claude's confused.

If you're vague, the LLM fills the gaps — sometimes with hallucinations.

Bad:

> Please remove all personally identifiable information from these customer feedback messages: {{FEEDBACK_DATA}}

Good:

> Your task is to anonymize customer feedback for our quarterly review.
>
> Instructions:
>
> 1. Replace customer names with "CUSTOMER\_[ID]" (e.g., "Jane Doe" → "CUSTOMER_001").
> 2. Replace email addresses with "EMAIL\_[ID]@example.com".
> 3. Redact phone numbers as "PHONE\_[ID]".
> 4. Leave specific product names (e.g., "AcmeCloud") intact.
> 5. If no PII is found, copy the message verbatim.
> 6. Output only the processed messages, separated by "---".
>
> Data to process: {{FEEDBACK_DATA}}

#### Describe the LLM's role

Use the `system` parameter to set Claude's role; put task-specific instructions in the user turn. This improves accuracy, focus, and tone.

```java
ConverseRequest converseRequest = ConverseRequest.builder()
  .modelId("claude-3-7-sonnet")
  .system(SystemContentBlock.fromText("You are a seasoned data scientist at a Fortune 500 company."))
  .messages(Message.builder()
    .role(ConversationRole.USER)
    .content(ContentBlock.fromText("Analyze this dataset for anomalies: <dataset>{{DATASET}}</dataset>"))
    .build())
  .inferenceConfig(config -> config
    .maxTokens(2000)
    .temperature(1.0f)
    .build())
  .build();
```

#### Use examples

Well-crafted examples improve:

- **Accuracy**: reduce misinterpretation of instructions
- **Consistency**: enforce uniform structure and style
- **Performance**: boost Claude's ability on complex tasks

Effective examples are:

- **Relevant**: mirror your actual use case
- **Diverse**: vary enough that Claude doesn't pick up unintended patterns
- **Clear**: wrapped in `<example>` tags (nested in `<examples>` if multiple)

Bad:

> Will Santa bring me presents on Christmas?

Good:

> Please complete the conversation by writing the next line, speaking as "A".
> Q: Is the tooth fairy real?
> A: Of course, sweetie. Wrap up your tooth and put it under your pillow tonight. There might be something waiting for you in the morning.
> Q: Will Santa bring me presents on Christmas?

#### Use XML tags

Claude is very good at parsing prompt sections separated by XML tags.

Bad:

> You're a financial analyst at AcmeCorp. Generate a Q2 financial report for our investors. Include sections on Revenue Growth, Profit Margins, and Cash Flow, like with this example from last year: {{Q1_REPORT}}. Use data points from this spreadsheet: {{SPREADSHEET_DATA}}. The report should be extremely concise, to the point, professional, and in list format. It should highlight both strengths and areas for improvement.

Good:

> You're a financial analyst at AcmeCorp. Generate a Q2 financial report for our investors.
>
> AcmeCorp is a B2B SaaS company. Our investors value transparency and actionable insights.
>
> Use this data for your report: <data>{{SPREADSHEET_DATA}}</data>
>
> <instructions>
> 1. Include sections: Revenue Growth, Profit Margins, Cash Flow.
> 2. Highlight strengths and areas for improvement.
> </instructions>
>
> Make your tone concise and professional. Follow this structure:
> <formatting_example>{{Q1_REPORT}}</formatting_example>

Nest tags for hierarchical content:

```xml
<documents>
  <document index="1">
    <source>patient_symptoms.txt</source>
    <document_content>{{PATIENT_SYMPTOMS}}</document_content>
  </document>
  <document index="2">
    <source>patient_records.txt</source>
    <document_content>{{PATIENT_RECORDS}}</document_content>
  </document>
</documents>
```

#### Place long documents above the query

Put long inputs (~20K+ tokens) near the top of your prompt, above your query, instructions, and examples. Per Anthropic, queries at the end can improve response quality by up to 30%, especially with complex, multi-document inputs.

Bad:

```html
Analyze the annual report and competitor analysis. Identify strategic advantages and recommend Q3 focus areas.

<documents>
  <document index="1">
    <source>annual_report_2023.pdf</source>
    <document_content>{{ANNUAL_REPORT}}</document_content>
  </document>
  <document index="2">
    <source>competitor_analysis_q2.xlsx</source>
    <document_content>{{COMPETITOR_ANALYSIS}}</document_content>
  </document>
</documents>
```

Good:

```html
<documents>
  <document index="1">
    <source>annual_report_2023.pdf</source>
    <document_content>{{ANNUAL_REPORT}}</document_content>
  </document>
  <document index="2">
    <source>competitor_analysis_q2.xlsx</source>
    <document_content>{{COMPETITOR_ANALYSIS}}</document_content>
  </document>
</documents>

Analyze the annual report and competitor analysis. Identify strategic advantages and recommend Q3 focus areas.
```

#### Prefill the beginning of response

Add initial text in an `Assistant` message and Claude will continue from where it leaves off:

```java
ConverseRequest converseRequest = ConverseRequest.builder()
  .modelId("claude-3-7-sonnet")
  .messages(Message.builder()
    .role(ConversationRole.USER)
    .content(ContentBlock.fromText("What is your favorite color?"))
    .build())
  .messages(Message.builder()
    .role(ConversationRole.ASSISTANT)
    .content(ContentBlock.fromText("As an AI assistant, I don't have a favorite color, But if I had to pick, it would be green because"))
    .build())
  .inferenceConfig(config -> config
    .maxTokens(2000)
    .temperature(1.0f)
    .build())
  .build();
```

> **NOTE**: Prefill content cannot end with trailing whitespace. A prefill like `"As an AI assistant, I "` will error.

Prefill is also useful for forcing JSON-only output. Without a prefill, Claude often wraps JSON in preamble and explanation text. Adding an assistant message with just `"{"` causes Claude to emit JSON only:

```java
.messages(Message.builder()
  .role(ConversationRole.ASSISTANT)
  .content(ContentBlock.fromText("{"))
  .build())
```

Result:

```json
{
  "name": "SmartHome Mini",
  "size": "5 inches wide",
  "price": "$49.99",
  "colors": ["black", "white"]
}
```
