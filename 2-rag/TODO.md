# Retrieval-Augmented Generation (RAG) Workflow

This exercise provides a simple implementation of a RAG (Retrieval-Augmented Generation) workflow using Java and Temporal.

First take a look at the `RagClient.java` file. This file contains the main logic for the RAG workflow, including setting up the Temporal client and executing the Workflows.

You should run the provided Worker and Client first to see the default behavior.

Take a look at the Temporal Web UI to take a look at the Workflow executions. You can access it at:
<http://localhost:8233/namespaces/default/workflows>

Take a look at the Qdrant Web UI to see the documents that have been added to the Vector Database. You can access it at:
<http://localhost:6333/dashboard#/collections>

Once you have run the default implementation, you can start modifying the code to customize the RAG workflow to learn more about the different parts. A good place to start is the `activities/ActivitiesImpl.java` file, which contains the implementation of the activities that are used in the Workflows.

You can look for `TODO_RAG` comments in the code to find areas that you might want to modify or enhance.

Remember to restart the Worker (and Client) after making changes to the code.

You should also delete the document chunks from the Qdrant Database before running the Client again to avoid duplicates. You can access it at: <http://localhost:6333/dashboard#/collections>
