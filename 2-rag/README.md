# Exercise 2 - Retrieval Augmented Generation

## Goals

Understand how information from documents, webpages, code, etc. can be converted into a numerical representation, stored in a vector database, and retrieved for use in the LLM context.

## What you need to know

Retrieval-Augmented Generation (RAG) enhances LLMs by combining their generative capabilities with information from external sources such as databases, documents, or the web. This is especially useful for surfacing domain-specific information from an organization's internal knowledge base without retraining or fine-tuning the base model.

RAG helps mitigate common LLM problems:

- Presenting false information when a topic isn't well represented in the training data.
- Returning out-of-date or generic answers when a specific, current response is needed.

## How it works

Documents are broken into manageable chunks, converted into vector representations, and stored in a vector database. When a user asks a question:

1. Run the question through the same embedding model used on the documents.
2. Use semantic search against the vector database to fetch the most relevant chunks.
3. Combine those chunks with the original prompt to form the model's context.
4. Feed the context to the model and return its response.

![RAG general flow](../.images/RAG-flowchart.png)

### Document Splitting

LLMs have a limited context length, so documents must be split into smaller chunks so we can work with only the most relevant parts.

[LangChain4j](https://docs.langchain4j.dev/tutorials/rag/#document-splitter) provides several splitting strategies. Our examples use `DocumentByParagraphSplitter`. Other options include:

- `DocumentByLineSplitter`
- `DocumentBySentenceSplitter`
- `DocumentByWordSplitter`
- `DocumentByCharacterSplitter`
- `DocumentByRegexSplitter`

When instantiating a splitter, you specify the chunk size and the overlap between adjacent chunks. Overlap helps preserve context across chunk boundaries. Tuning these parameters to your document structure can significantly improve output quality.

[ChunkViz](https://chunkviz.up.railway.app/) is a useful tool for visualizing how text is split and where overlap is applied. You can upload your own text to experiment with different parameters.

### Embeddings

Embedding models convert text into vectors that capture its semantic meaning. Our examples use AWS Bedrock's [`amazon.titan-embed-text-v2:0`](https://docs.aws.amazon.com/bedrock/latest/userguide/titan-embedding-models.html), which accepts up to 8,192 tokens (or 50,000 characters) and outputs a 1,024-dimension vector. It is optimized for retrieval tasks such as RAG, classification, and document search, and supports 100+ languages with a focus on English.

### Vector Database

Vector databases store vectors and use techniques like approximate nearest-neighbor search to quickly find the most similar vectors to a query. Many options exist, including plugins that add vector support to PostgreSQL or SQLite.

Our examples use [Qdrant](https://qdrant.tech/) (read: "quadrant"), an open-source vector similarity search engine with extended filtering support, making it well-suited for neural/semantic matching and faceted search.

### Model Context

As covered in the Prompt Engineering exercise, the System Prompt defines the model's role, expected response type, and any task-relevant information. With RAG, the System Prompt is also where we inject context retrieved from the vector database:

```text
You are a helpful assistant that provides information about building AI Agents with Temporal. Answer questions using the provided context, which comes from documents, webpages, or other sources stored in a vector database.

Additional Context:
{context}

If you do not know the answer, say "I don't know" instead of making up an answer. Keep answers to a couple of paragraphs separated by newlines, and use Markdown formatting when appropriate.
```

The `{context}` placeholder is replaced with relevant chunks retrieved from the vector database before the model is called.
