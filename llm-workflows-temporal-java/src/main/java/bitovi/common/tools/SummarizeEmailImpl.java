package bitovi.common.tools;

import java.util.ArrayList;

import bitovi.workflows.AgentGoal.helpers.AgentToolArgument;
import bitovi.workflows.AgentGoal.helpers.AgentToolArgumentType;
import bitovi.workflows.AgentGoal.helpers.AgentToolDefinition;
import io.github.ollama4j.tools.Tools;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;

public class SummarizeEmailImpl {

        public static String execute(Document toolUseInput) {
                String str = toolUseInput.toString();
                System.out.println(str);
                return str;
        }

        public static Tool getBedrockTool() {
                ArrayList<AgentToolArgument> args = new ArrayList<>();
                args.add(new AgentToolArgument("summary", "A brief one-line or two-line summary of the email.",
                                AgentToolArgumentType.STRING, true));

                args.add(new AgentToolArgument("escalate_complaint",
                                "Indicates if this email is serious enough to be immediately escalated for further review.",
                                AgentToolArgumentType.BOOLEAN, true));

                args.add(new AgentToolArgument("level_of_concern",
                                "Rate the level of concern for the above content on a scale from 1-10.",
                                AgentToolArgumentType.INTEGER, true));

                args.add(new AgentToolArgument("overall_sentiment",
                                "The sender's overall sentiment. ex. Positive, Neutral, Negative.",
                                AgentToolArgumentType.STRING, true));

                args.add(new AgentToolArgument("supporting_business_unit",
                                "The internal business unit that this email should be routed to.",
                                AgentToolArgumentType.STRING, true));

                AgentToolDefinition toolDefinition = new AgentToolDefinition("summarize_email",
                                "Summarize email content.",
                                args);
                return toolDefinition.getBedrockTool();
        }

        public static Tools.ToolSpecification getOllamaTool() {
                throw new UnsupportedOperationException("Unimplemented method 'getOllamaTool'");
        }
}
