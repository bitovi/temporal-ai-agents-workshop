# Agent Memory

## Initial Example
1. Update `.env` in root with your own username for `USER_ACTOR_ID`
2. Run Task: Sync Environments
3. Run Task: Docker Compose Down 
4. Run Task: Docker Compose Up
5. Launch: Exercise 7 - Worker
6. Launch: Exercise 7 - Client
7. Observe:
    - Open the latest workflow in the [temporal ui](http://localhost:8233/)
    - Click on the Thought Activity to see the question asked and answered
    - In `AgentMemoryClient.java` we asked the agent what he knew about us. If you haven't interacted with the agent yet, it shouldn't have any LTM about you yet.

## TODO
1. Open the Chat Web UI: `http://localhost:3000/` and start a conversation. Tell it some of your personal preferences (favorite color, favorite coffee, etc.)
2. (Optional) Navigate to the AWS console, find the memory resource and watch it extract LTM records async.
3. After a few minutes, start a completely new conversation and ask the agent what it knows about you.
4. Observe that the agent should be able "remember" things about you.

Search for `TODO_MEMORY` in the code:

- TODO_MEMORY: Experiment by including memory records queried from different strategies (i.e. episodic, semantic, summary)