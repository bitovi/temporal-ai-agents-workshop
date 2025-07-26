# Tool Calling Workflow

This exercise provides a simple implementation of a Tool Calling workflow using Java and Temporal.

First take a look at the `ToolCallingWorkflowImpl.java` file. The `execute` method is where the workflow logic is defined. The workflow uses a chat history to interact with an AI model, which can call tools based on the conversation context.

You should run the provided Worker and Client first to see the default behavior.

Run the workflow without modiications first by using the vscode launch configuration for 'Exercise 2 - Worker' and then 'Exercise 2 - Client'. This will start the Temporal worker and client, allowing you to see how the workflow executes with the provided chat history.

Take a look at the Temporal Web UI to observe the Workflow executions. You can access it at:
<http://localhost:8233/namespaces/default/workflows>

Once you have run the default implementation, you can start modifying the code to customize the Tool Calling workflow.

Using the provided `WeatherTool.java` as an example, create your own tool implementation in the `DefineYourOwnTool.java` file. The TODOs in that file will provide guidance on what to implement.

You will also need to update the `AWS.java` file to register your new tool. Look for the comments in the `AWS.java` file that indicate where to add your tool.

You will also need to update the `ActivitiesImpl.java` file to correctly handle the tool call. Look for the TODOs in that file for guidance on what to implement.

Once you have implemented your tool, update the sample chat history in the `ToolCallingWorkflowImpl.java` file to include a question for the model that would require your new tool to be called. This will allow you to test your tool implementation within the workflow.
