#!/bin/bash

# Create list of directories to copy the .env file into
dirs=(0-environment-setup 1-prompt-engineering 2-rag 3-tool-calling 4-mcp 5-agent-workflow 6-agent-decisions 7-agent-memory 8-agent-to-agent)

for dir in "${dirs[@]}"; do
  echo "Cleaning build artifacts in $dir"
  cd "$dir/java"
  mvn clean compile -U
  cd ../../
done