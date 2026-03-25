package bitovi;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.Claude;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;

import bitovi.common.BedrockModel;
import bitovi.common.Config;
import bitovi.data.PlayerAccountService;
import bitovi.data.records.PlayerAccount;

public class SupportAgent {

        public static BaseAgent ROOT_AGENT = bedrockAgent();

        public static String instructions = """
                        You are a helpful assistant that provides information about player accounts and their purchase history.
                        """;

        private static BaseAgent defaultAgent() {
                return LlmAgent.builder()
                                .name("support-agent")
                                .description("Assists players with the account information and purchase history")
                                .instruction(instructions)
                                .model("gemini-2.5-flash")
                                .tools(FunctionTool.create(SupportAgent.class, "getUserAccount"),
                                                FunctionTool.create(SupportAgent.class, "getPlayerPurchases"))
                                .build();
        }

        public static LlmAgent claudeAgent() {
                Config config = new Config();
                AnthropicClient anthropicClient = AnthropicOkHttpClient.builder()
                                .apiKey(config.getProperty("ANTHROPIC_API_KEY"))
                                .build();

                Claude claudeModel = new Claude(
                                "claude-sonnet-4-6", anthropicClient);

                return LlmAgent.builder()
                                .name("claude_direct_agent")
                                .model(claudeModel)
                                .instruction(instructions)
                                .tools(FunctionTool.create(SupportAgent.class, "getUserAccount"),
                                                FunctionTool.create(SupportAgent.class, "getPlayerPurchases"))
                                .build();
        }

        public static LlmAgent bedrockAgent() {
                Config config = new Config();
                BedrockModel bedrockModel = new BedrockModel(config.getProperty("AWS_MODEL_ID"));

                return LlmAgent.builder()
                                .name("bedrock_agent")
                                .model(bedrockModel)
                                .instruction(instructions)
                                .tools(FunctionTool.create(SupportAgent.class, "getUserAccount"),
                                                FunctionTool.create(SupportAgent.class, "getPlayerPurchases"))
                                .build();
        }

        /** Mock tool implementation */
        @Schema(description = "Look up a player account by their player ID. Returns account summary without sensitive identity fields.")
        public static String getUserAccount(
                        @Schema(name = "playerId", description = "The ID of the player to look up. This should be in the format '#1234'") String playerId) {
                PlayerAccount pa = PlayerAccountService.getPlayerAccount(playerId);
                return pa != null ? pa.toString() : "Player account not found";
        }

        /** Mock tool implementation */
        @Schema(description = "Get the purchase history for a given player ID")
        public static String getPlayerPurchases(
                        @Schema(name = "playerId", description = "The ID of the player to get purchases for. This should be in the format '#1234'") String playerId) {
                PlayerAccount pa = PlayerAccountService.getPlayerAccount(playerId);
                return pa != null ? PlayerAccountService.getPlayerPurchases(playerId).toString()
                                : "Player account not found";
        }
}