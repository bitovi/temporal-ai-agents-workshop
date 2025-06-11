#!/bin/bash

# Simple OpenTelemetry Test Runner
# This script runs a basic test to verify OpenTelemetry tracing with Langfuse

echo "🧪 Simple OpenTelemetry + Langfuse Test"
echo "======================================="

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

# Run the simple test
echo "🚀 Running OpenTelemetry test..."
echo ""

mvn exec:java \
    -Dexec.mainClass="bitovi.examples.SimpleOTelTest" \
    -Dexec.args="" \
    -q

echo ""
echo "✅ Test completed!"
echo ""
echo "📊 Check your Langfuse dashboard for test traces:"
langfuse_host=$(grep "LANGFUSE_HOST" config.properties | cut -d'=' -f2 | tr -d '"')
echo "   ${langfuse_host}"
echo ""
echo "🔍 Look for traces with names like:"
echo "   - test.basic_span"
echo "   - test.parent_operation"
echo "   - test.error_span"
