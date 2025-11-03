#!/bin/bash
# This script should take the `.env` file and copy it into the 0-5 sub project directories.

if [ ! -f .env ]; then
  echo ".env file not found in the current directory."
  exit 1
fi

# Create list of directories to copy the .env file into
dirs=(0-environment-setup 1-prompt-engineering 2-rag 3-tool-calling 4-mcp 5-agent-workflow 6-agent-memory 7-agent-decisions 8-agent-to-agent)

# Clean up existing .env files in the target directories
for dir in "${dirs[@]}"; do
  echo "Removing existing .env from $dir"
  rm "$dir/.env"
  rm "$dir/java/.env"
  rm "$dir/typescript/.env"
done

# Copy the .env file into each of the specified directories
for dir in "${dirs[@]}"; do
  echo "Copying .env to $dir"
  cp .env "$dir/java/"
  cp .env "$dir/typescript/"
done