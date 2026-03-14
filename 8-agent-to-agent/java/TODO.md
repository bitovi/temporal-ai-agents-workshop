# Agent to Agent

## Initial Example
1. Run Task: Docker Compose Down
2. Run Task: Docker Compose Up
3. Launch: Exercise 8 - Worker
4. Launch: Exercise 8 - Client
5. Observe:
    - Open the latest workflow in the [temporal ui](http://localhost:8233/)
    - Click on the Action Activity to see the tool calls: first the agent registry
      lookup, then the A2A message exchange with the Riot Games Support Agent
    - In `AgentToAgentClient.java` we ask about recent purchases from Riot

## TODO

Search for `TODO_A2A` in the code:

- TODO_A2A: Experiment with different questions (billing issues, refund requests, etc.)