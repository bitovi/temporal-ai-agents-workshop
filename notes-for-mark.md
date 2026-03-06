# Handoff Notes
Quick notes for async handoff to Mark if neeed.

## Environment Setup
- copy and rename .env.example to .env (root dir)
- only secrets redacted:
    - aws secrets (get from console)
    - brave api key (i can share if you need)
- I "task-ified" `sync-env.sh` and have been using that as a central source of truth which get's coppied into each exercise.
- The docker compose setup also passes some for the env vars from `/.env` into the containers

## Exercise 0
- Docker Compose Up (task)
- Run worker (launch config)
- Run client (launch config)
- temporal ui: `http://localhost:8233/` (source in `/agent-chat-server` (dockerized))

## Exercises 1-4
- I have not checked if these still work (maybe it doesn't matter)

## Exercise 5 (Agentic Workflow)
- temporal ui: `http://localhost:8233/`
- Web Chat UI: `http://localhost:3000/`
    - "New Conversation": starts new temporal workflow
    - "Name" field: doesn't do anything right now
    - "Send Message": starts ReAct loop.
    - "Compact": Manually triggers continue-as-new/compaction
    - "Exit": ends the temporal workflow

## Exercise 6 (Agent Decisions)
- see `6-agent-decisions/java/TODO.md`
- `6-agent-decisions/java/src/main/resources/word-problems`
    - contains some sample word problems I scraped from on online textbook
    - example usage in `6-agent-decisions/java/src/main/java/bitovi/AgentDecisionsClient.java`
- this exercise shows a rough calculation of "reasoning" token usage
- [extended thinking docs](https://docs.aws.amazon.com/nova/latest/nova2-userguide/extended-thinking.html)

## Exercise 7 (Agent Memory)
- see `7-agent-memory/java/TODO.md`
- AWS Bedrock AgentCore Memory Resource
    - Here's the one I provisioned in the "sandbox" bitovi aws account: 
        - [memory resource](https://us-east-2.console.aws.amazon.com/bedrock-agentcore/memory/Riot_Bitovi_Temporal_AI_Workshop_Memory-cPS5wpCWII?region=us-east-2)
    - All the attendees should be able to use the same memory resource concurrently
    - The LTM records are extracted into granular namespaces, so as long as each attendee has setup a unique `USER_ID` in the `.env` file, their agents should only have memories about them.
    - For provisioning a memory resource for Riot attendees I made some utility launch configs:
        - "Exercise 7 - Create Memory"
        - "Exercise 7 - Delete Memory"
        - (once created, update the `AWS_BEDROCK_AGENTCORE_MEMORY_ID` value in `/.env`)
- There are also some other utility launch configs we might choose to keep or discard:
    - "Exercise 7 - List Events": Lists STM events
    - "Exercise 7 - List Memory Records": Lists LTM Records
    - "Exercise 7 - Retrieve Memory Records": Semantic search for LTM Records

## Exercise 8 (Agent To Agent Protocol)
- see `8-agent-to-agent/java/TODO.md`
- I chose to use gutendex api instead tmdb for legal reasons
- source of the book agent is in `/book-agent-server/` (dockerized)
- pretty minimal and rough impl of the A2A protocol integration

