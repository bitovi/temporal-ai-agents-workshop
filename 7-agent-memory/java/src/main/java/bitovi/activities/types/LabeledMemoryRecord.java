package bitovi.activities.types;

import software.amazon.awssdk.services.bedrockagentcorecontrol.model.MemoryStrategyType;

public record LabeledMemoryRecord(MemoryStrategyType strategy, String payload) {
    public String toXMLString() {
        return String.format("<%s>%s</%s>", strategy.toString(), payload, strategy.toString());
    }
}
