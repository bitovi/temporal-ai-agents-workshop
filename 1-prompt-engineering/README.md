# Exercise 1 - Prompt Engineering

## Goals

The goal of this first exercise is to learn the best practices of prompt engineering. We will learn about these best practices and apply them to guide an LLM to evaluate a customer service agent's response to a customer's question based on a series of guidelines.

## What you need to know

Each LLM has its own set of guidelines for how to optimize your text prompts to get the highest quality responses. [Bedrock](https://docs.aws.amazon.com/bedrock/latest/userguide/prompt-engineering-guidelines.html) has a good list of each model's prompt guides. For this workshop, we will use Claude 3.7 Sonnet, so you can refer to [Anthropic's guide](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview). Anthropic also has an [interactive guide](https://github.com/anthropics/prompt-eng-interactive-tutorial/tree/master/AmazonBedrock/anthropic) that you can try on your own.

### Parts of a Prompt

1. The task you want the LLM to perform
2. Context for the task
3. Examples
4. Prefilled response content

### Prompt Structure

- Model: the model you want to call
- System Message (optional): used to provide context, instructions, and guidelines
- Messages: alternating messages between a `user` and `assistant`. The first message must be a `user` and they should alternate.
- Maximum number of output tokens: the maximum number of tokens to generate
- Temperature: the degree of variability in Claude's response. `0` is the most deterministic. `1` is the most variable.

This structure is more obvious when using the InvokeModel API:

```json
{
    "anthropic_version": "bedrock-2023-05-31",
    "system": <system prompt>
    "messages": [
        {
            "role": "user",
            "content": [
            {
                "type": "text",
                "text": <user message>
            }
            ]
        },
        {
            "role": "assistant",
            "content": [
            {
                "type": "text",
                "text": <assistant message>
            }
            ]
        },
        ...
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
  .messages([<user message>, <system message>, ...])
  .inferenceConfig(config -> config
    .maxTokens(<max tokens>)
    .temperature(<temperature>)
    .build())
.build();
```

### Context Windows

All LLMs have context length limits. Claude's limit is 200k tokens, and a token is approximately 3.5 English characters. There is a token-counting API available.

If you're just making a single request, the window includes:

- your prompt
- the response, which will be limited to the maxTokens you set in the API request

### Techniques

#### Be clear and direct

Use clear and direct instructions; the LLM has no other context outside of the instructions you provide. Think of the LLM as "a brilliant but very new employee (with amnesia) who needs explicit instructions." ([Anthropic's Guide](https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/be-clear-and-direct))

Golden Rule of Clear Prompting:

> Show your prompt to a colleague or friend and have them follow the instructions themselves to see if they can produce the result you want. If they're confused, Claude's confused.

If you aren't explicit about exactly what you want the LLM to do and how you want it respond, it will fill in the vagueness gaps, potentially with hallucinated information.

Bad:

> Please remove all personally identifiable information from these customer feedback messages: {{FEEDBACK_DATA}}

Good:

> Your task is to anonymize customer feedback for our quarterly review.
>
> Instructions:
>
> 1. Replace all customer names with “CUSTOMER\_[ID]” (e.g., “Jane Doe” → “CUSTOMER_001”).
> 2. Replace email addresses with “EMAIL\_[ID]@example.com”.
> 3. Redact phone numbers as “PHONE\_[ID]“.
> 4. If a message mentions a specific product (e.g., “AcmeCloud”), leave it intact.
> 5. If no PII is found, copy the message verbatim.
> 6. Output only the processed messages, separated by ”---”.
>
> Data to process: {{FEEDBACK_DATA}}

#### Describe the LLM's role

Use the system parameter to set Claude’s role. Put everything else, like task-specific instructions, in the user turn instead. This makes the LLM more accurate and focused on the task. It can also affect the communication style of the response.

```java
ConverseRequest converseRequest = ConverseRequest.builder()
  .modelId("claude-3-7-sonnet")
  .system(SystemContentBlock.fromText("You are a seasoned data scientist at a Fortune 500 company."))
  .messages(Message.builder()
    .role(ConversationRole.USER)
    .content(ContentBlock.fromText( "Analyze this dataset for anomalies: <dataset>{{DATASET}}</dataset>"))
    .build())
  .inferenceConfig(config -> config
    .maxTokens(2000)
    .temperature(1.0f)
    .build())
.build();
```

#### Use examples

Providing a few well-crafted examples will improve:

- Accuracy: Examples reduse misinterpretation of instructions
- Consistency: Examples enforce uniform structure and style
- Perofrmance: Well-chose examples boost Claude's ability to handle complex tasks

For maximum effectiveness, make sure that your examples are:

- Relevant: Your examples mirror your actual use case
- Diverse: our examples vary enough that Claude doesn’t inadvertently pick up on unintended patterns.
- Clear: Clear: Your examples are wrapped in <example> tags (if multiple, nested within <examples> tags) for structure. (more on this in the next section)

Bad:

> Will Santa bring me presents on Christmas?

Good:

> Please complete the conversation by writing the next line, speaking as "A".
> Q: Is the tooth fairy real?
> A: Of course, sweetie. Wrap up your tooth and put it under your pillow tonight. There might be something waiting for you in the morning.
> Q: Will Santa bring me presents on Christmas?

#### Use XML tags

LLMs, specifically Claude, are very good at understanding parts of a prompt separated by XML tags.

Bad:

> You’re a financial analyst at AcmeCorp. Generate a Q2 financial report for our investors. Include sections on Revenue Growth, Profit Margins, and Cash Flow, like with this example from last year: {{Q1_REPORT}}. Use data points from this spreadsheet: {{SPREADSHEET_DATA}}. The report should be extremely concise, to the point, professional, and in list format. It should and highlight both strengths and areas for improvement.

Good:

> You’re a financial analyst at AcmeCorp. Generate a Q2 financial report for our investors.
>
> AcmeCorp is a B2B SaaS company. Our investors value transparency and actionable insights.
>
> Use this data for your report:<data>{{SPREADSHEET_DATA}}</data>
>
> <instructions>
> 1. Include sections: Revenue Growth, Profit Margins, Cash Flow.
> 2. Highlight strengths and areas for improvement.
> </instructions>
>
> Make your tone concise and professional. Follow this structure:
> <formatting_example>{{Q1_REPORT}}</formatting_example>

It also helps to nest tags for hierarchical content:

```xml
<documents>
  <document index="1">
    <source>patient_symptoms.txt</source>
    <document_content>
      {{PATIENT_SYMPTOMS}}
    </document_content>
  </document>
  <document index="2">
    <source>patient_records.txt</source>
    <document_content>
      {{PATIENT_RECORDS}}
    </document_content>
  </document>
  <document index="3">
    <source>patient01_appt_history.txt</source>
    <document_content>
      {{PATIENT01_APPOINTMENT_HISTORY}}
    </document_content>
  </document>
</documents>
```

#### Place long documents above the query

Place your long documents and inputs (~20K+ tokens) near the top of your prompt, above your query, instructions, and examples.

According to Anthropic:

> Queries at the end can improve response quality by up to 30% in tests, especially with complex, multi-document inputs.

Bad:

```html
Analyze the annual report and competitor analysis. Identify strategic advantages and recommend Q3 focus areas.

<documents>
  <document index="1">
    <source>annual_report_2023.pdf</source>
    <document_content>
      {{ANNUAL_REPORT}}
    </document_content>
  </document>
  <document index="2">
    <source>competitor_analysis_q2.xlsx</source>
    <document_content>
      {{COMPETITOR_ANALYSIS}}
    </document_content>
  </document>
</documents>
```

Good:

```html
<documents>
  <document index="1">
    <source>annual_report_2023.pdf</source>
    <document_content>
      {{ANNUAL_REPORT}}
    </document_content>
  </document>
  <document index="2">
    <source>competitor_analysis_q2.xlsx</source>
    <document_content>
      {{COMPETITOR_ANALYSIS}}
    </document_content>
  </document>
</documents>

Analyze the annual report and competitor analysis. Identify strategic advantages and recommend Q3 focus areas.
```

#### Prefill the beginning of response

Add initial text in the `Assistant` message and Claude will fill in where it leaves off:

```java
ConverseRequest converseRequest = ConverseRequest.builder()
  .modelId("claude-3-7-sonnet")
  .messages(Message.builder()
    .role(ConversationRole.USER)
    .content(ContentBlock.fromText( "What is your favorite color?"))
    .build())
  .messages(Message.builder()
    .role(ConversationRole.ASSISTANT)
    .content(ContentBlock.fromText( "As an AI assistant, I don't have a favorite color, But if I had to pick, it would be green because"))
    .build())
  .inferenceConfig(config -> config
    .maxTokens(2000)
    .temperature(1.0f)
    .build())
.build();
```

> NOTE: The prefill content cannot end with trailing whitespace. A prefill like "As an AI assistant, I " (with a space at the end) will result in an error.

You can also use the prefill content to force Claude to output JSON without any initial "preamble" text:

Bad:

```java
ConverseRequest converseRequest = ConverseRequest.builder()
  .modelId("claude-3-7-sonnet")
  .messages(Message.builder()
    .role(ConversationRole.USER)
    .content(ContentBlock.fromText("""
Extract the name, size, price, and color from this product description as a JSON object:

<description>
The SmartHome Mini is a compact smart home assistant available in black or white for only $49.99. At just 5 inches wide, it lets you control lights, thermostats, and other connected devices via voice or app—no matter where you place it in your home. This affordable little hub brings convenient hands-free control to your smart devices.
</description>
    """))
    .build())
  .inferenceConfig(config -> config
    .maxTokens(2000)
    .temperature(1.0f)
    .build())
.build();
```

With this prompt, Claude will respond with something like

> Here’s the extracted information in JSON format:
>
> ```json
> {
> “name”: “SmartHome Mini”,
> “size”: “5 inches wide”,
> “price”: “$49.99”,
> “colors”: [“black”, “white”]
> }
> ```
>
> I’ve extracted the following details from the product description:
>
> - Name: SmartHome Mini
> - Size: 5 inches wide
> - Price: $49.99
> - Colors: Available in black and white
>
> The JSON object provides a structured format that’s easy for programs to parse and use. Let me know if you need any modifications or have any other questions!

Good:

```java
ConverseRequest converseRequest = ConverseRequest.builder()
  .modelId("claude-3-7-sonnet")
  .messages(Message.builder()
    .role(ConversationRole.USER)
    .content(ContentBlock.fromText("""
Extract the name, size, price, and color from this product description as a JSON object:

<description>
The SmartHome Mini is a compact smart home assistant available in black or white for only $49.99. At just 5 inches wide, it lets you control lights, thermostats, and other connected devices via voice or app—no matter where you place it in your home. This affordable little hub brings convenient hands-free control to your smart devices.
</description>
    """))
    .build())
  .messages(Message.builder()
    .role(ConversationRole.ASSISTANT)
    .content(ContentBlock.fromText( "{"))
    .build())
  .inferenceConfig(config -> config
    .maxTokens(2000)
    .temperature(1.0f)
    .build())
.build();
```

With this prefill, Claude will respond with solely JSON:

```json
{
Assistant (Claude’s response)	“name”: “SmartHome Mini”,
“size”: “5 inches wide”,
“price”: “$49.99”,
“colors”: [
“black”,
“white”
]
}
```
