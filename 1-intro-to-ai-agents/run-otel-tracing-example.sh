#!/bin/bash

# OpenTelemetry + Langfuse Tracing Example Runner
# This script runs the LLM tracing example that sends OpenTelemetry traces to Langfuse

echo "🔍 OpenTelemetry + Langfuse LLM Tracing Example"
echo "=============================================="

# Check if config exists
if [ ! -f "config.properties" ]; then
    echo "❌ config.properties not found. Please create it from config.properties.example"
    exit 1
fi

# Check if LANGFUSE_HOST is configured
if ! grep -q "LANGFUSE_HOST" config.properties; then
    echo "❌ LANGFUSE_HOST not configured in config.properties"
    echo "Please add: LANGFUSE_HOST=http://localhost:3000"
    exit 1
fi

# Build the project
echo "🔨 Building project..."
mvn compile -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✅ Build successful"

# Run the tracing example
echo "🚀 Running OpenTelemetry + Langfuse tracing example..."
echo ""

mvn exec:java \
    -Dexec.mainClass="bitovi.examples.OTelTracingExample" \
    -Dexec.args="" \
    -q

echo ""
echo "✅ Example completed!"
echo ""
echo "📊 Check your Langfuse dashboard for traces:"
langfuse_host=$(grep "LANGFUSE_HOST" config.properties | cut -d'=' -f2 | tr -d '"')
echo "   ${langfuse_host}"
echo ""
echo "🔗 Traces should appear in the 'Traces' section of your Langfuse dashboard"
echo "   Each LLM call will be captured with:"
echo "   - Input prompts and messages"
echo "   - Model responses"
echo "   - Tool usage (if applicable)"
echo "   - Timing and performance metrics"
echo "   - Error details (if any)"
