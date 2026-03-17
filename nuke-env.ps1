# Clean build artifacts in all exercise directories

$dirs = @(
    "0-environment-setup",
    "1-prompt-engineering",
    "2-rag",
    "3-tool-calling",
    "4-mcp",
    "5-agent-workflow",
    "6-agent-decisions",
    "7-agent-memory",
    "8-agent-to-agent"
)

foreach ($dir in $dirs) {
    Write-Host "Cleaning build artifacts in $dir"
    Push-Location "$dir\java"
    mvn clean compile -U
    Pop-Location
}
