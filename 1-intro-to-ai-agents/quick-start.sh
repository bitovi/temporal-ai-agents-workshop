#!/bin/bash

echo "AWS Bedrock + MCP Tools Integration - Quick Start Guide"
echo "======================================================"
echo ""

# Check if config exists
if [ ! -f "config.properties" ]; then
    echo "❌ config.properties not found!"
    echo "Please copy config.properties.example to config.properties and configure:"
    echo "  - AWS credentials (ACCESS_KEY_ID, SECRET_ACCESS_KEY, SESSION_TOKEN)"
    echo "  - AWS Bedrock model ARN and ID"
    echo "  - MCP server token (LIFEFORCE_MCP_TOKEN)"
    echo ""
    echo "Example configuration:"
    echo "AWS_MODEL_ARN=arn:aws:bedrock:us-east-2:123456789:inference-profile/us.meta.llama3-1-8b-instruct-v1:0"
    echo "LIFEFORCE_MCP_TOKEN=your-token-here"
    exit 1
fi

echo "✅ Configuration found"
echo ""

echo "🔧 Compiling project..."
mvn compile

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "✅ Compilation successful"
echo ""

echo "🚀 Available Examples:"
echo ""
echo "1. Simple MCP-Bedrock Integration (no Temporal required):"
echo "   ./run-simple-mcp-bedrock.sh"
echo ""
echo "2. MCP Client Test (test MCP connectivity):"
echo "   ./run-mcp-test.sh"
echo ""
echo "3. Standalone Bedrock Test (test Bedrock with local tools):"
echo "   ./run-standalone.sh"
echo ""
echo "4. Full Temporal Workflow (requires Temporal server):"
echo "   docker-compose up temporal -d"
echo "   ./run-worker.sh &"
echo "   ./run-mcp-bedrock-demo.sh"
echo ""

echo "📚 Key Integration Features:"
echo ""
echo "• Hybrid Tool Execution: Local tools (cosine) + MCP tools (weather, etc.)"
echo "• Automatic Tool Routing: Bedrock decides which tools to call"
echo "• Error Handling: Graceful fallbacks when tools fail"
echo "• Standardized Interface: MCP protocol for consistent tool integration"
echo ""

echo "🔍 Architecture Overview:"
echo ""
echo "User Query → Bedrock Runtime → Tool Selection → Execution"
echo "                    ↓"
echo "              Tool Definitions"
echo "                    ↓"
echo "         Local Tools + MCP Tools"
echo ""

echo "Ready to start! Choose an example above to begin."
