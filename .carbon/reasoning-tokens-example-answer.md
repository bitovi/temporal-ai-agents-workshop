# Reasoning Tokens Example

## Qwen 3.5:9b Output

```text
Hey there! That's a really interesting question about LLM Reasoning Tokens. While they aren't special symbols like `<B>` or `</S>`, these tokens refer to the
sequence of words a model generates when it performs Chain-of-Thought reasoning. Basically, instead of jumping straight to an answer, the model outputs intermediate
steps that show its logical deduction process before finalizing the output.

When a model encounters a complex task—like solving a math problem or deciphering a logic puzzle—it often uses these reasoning tokens to break down the issue into
smaller parts. This is why you sometimes see models pause and "think" aloud in text; that's simply a series of standard tokens representing internal logic rather
than final information. It allows the model to self-correct and improve accuracy before responding to the user.

The importance of these tokens lies in efficiency and quality. Researchers are always studying how many reasoning steps a model needs versus when it has thought
enough, which is crucial for managing computational costs. While tokenization varies by model architecture, optimizing this "thinking" budget is a major focus in
advanced AI development right now to make models smarter without wasting too much compute!
```

## GPT-OSS 20b Output

```text
Large Language Models (LLMs) like GPT‑4 don’t “see” words as whole units; they operate on **tokens**—sub‑word pieces produced by a tokenizer (e.g., Byte‑Pair
Encoding or SentencePiece). A token can be a full word, part of a word, or even punctuation. The model’s attention, self‑attention, and hidden states are all
computed over these token embeddings.

When people talk about **“reasoning tokens”**, they’re usually referring to the tokens that comprise the model’s step‑by‑step reasoning when it generates an answer.
For instance, if a user asks a math problem, the LLM might output:

Step 1: …
Step 2: …
Answer: …

Each word or sub‑word in that explanation is a token. There isn’t a special “reasoning” token type—rather, the model is conditioned (via prompt engineering or
instruction tuning) to produce a chain of reasoning, and the resulting tokens are just normal tokens that happen to form a logical sequence. Understanding token
counts is important because the cost and latency of a request depend on how many tokens are sent in the prompt plus how many the model produces.
```
