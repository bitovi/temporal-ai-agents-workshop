package bitovi;


import bitovi.common.Config;
import bitovi.common.aws.AgentCoreMemory;
import software.amazon.awssdk.services.bedrockagentcorecontrol.model.DeleteMemoryResponse;

/**
 * A handy utility for deleting an existing AWS Bedrock Agent Core Memory instance.
 * Make sure {@code AWS_BEDROCK_AGENTCORE_MEMORY_ID} is set in your {@code .env} file,
 * then run this using the "DeleteMemory" run configuration to permanently remove
 * the memory resource and all stored records associated with it.
 */
public class DeleteMemory {

    public static void main(String[] args) throws Exception {
        Config config = new Config();


        String memoryId = config.getProperty("AWS_BEDROCK_AGENTCORE_MEMORY_ID");
        
        if (memoryId == null || memoryId.isEmpty()) {
            System.err.println("Memory ID not found in config");
            System.exit(1);
        }
        
        DeleteMemoryResponse response = AgentCoreMemory.deleteMemory(memoryId);
        
        if (response == null) {
            System.err.println("Failed to delete memory - invalid response");
            System.exit(1);
        }

        System.out.println("Memory deleted successfully: " + memoryId);
    }
}
