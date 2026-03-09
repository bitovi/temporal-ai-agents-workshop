package bitovi;


import bitovi.common.Config;
import bitovi.common.aws.AgentCoreMemory;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.CreateMemoryResponse;

/**
 * A handy utility for provisioning a new AWS Bedrock Agent Core Memory instance.
 * Run this using the "CreateMemory" run configuration to create the memory resource
 * that the agent will use to store and retrieve conversation history across sessions.
 */
public class CreateMemory {

	public static void main(String[] args) throws Exception {
		Config config = new Config();

		// Hardcoded memory name
		CreateMemoryResponse response = AgentCoreMemory.createMemory();
		
		if (response == null || response.memory() == null || response.memory().id() == null) {
			System.err.println("Failed to create memory - invalid response");
			System.exit(1);
		}
		
		System.out.println("Memory created successfully!");
		System.out.println("Memory ID: " + response.memory().id());
		System.out.println("Memory Name: " + response.memory().name());
		System.out.println("Memory Description: " + response.memory().description());
		System.out.println("Memory Event ExpiryDuration (Days): " + response.memory().eventExpiryDuration());
	}
}
