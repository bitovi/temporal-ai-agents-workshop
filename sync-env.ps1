# This script takes the `.env` file and copies it into the 0-8 sub project directories.

if (-not (Test-Path ".env")) {
    Write-Error ".env file not found in the current directory."
    exit 1
}

$dirs = @(
    "0-environment-setup",
    "1-prompt-engineering",
    "2-rag",
    "3-tool-calling",
    "4-mcp",
    "5-agent-workflow",
    "6-agent-decisions",
    "7-agent-memory",
    "8-agent-to-agent",
    "support-agent-server-adk"
)

# Clean up existing .env files in the target directories
foreach ($dir in $dirs) {
    Write-Host "Removing existing .env from $dir"
    Remove-Item "$dir\.env" -ErrorAction SilentlyContinue
    Remove-Item "$dir\java\.env" -ErrorAction SilentlyContinue
    Remove-Item "$dir\typescript\.env" -ErrorAction SilentlyContinue
}

# Copy the .env file into each of the specified directories
foreach ($dir in $dirs) {
    Write-Host "Copying .env to $dir"
    Copy-Item ".env" "$dir\java\.env"
    Copy-Item ".env" "$dir\typescript\.env"
}
