#!/bin/bash
mvn compile
mvn compile exec:java -Dexec.mainClass="bitovi.SimpleMCPBedrockExample"
