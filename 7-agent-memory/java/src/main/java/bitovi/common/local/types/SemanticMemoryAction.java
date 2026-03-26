package bitovi.common.local.types;

import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

public record SemanticMemoryAction(ActionType operation,
        SemanticMemoryRecord memory) {

    private enum ActionType {
        AddMemory,
        UpdateMemory,
        SkipMemory
    }

    public static SemanticMemoryAction fromJson(JSONObject obj) throws JSONException {
        String operation = obj.getString("operation");
        switch (operation) {
            case "AddMemory" -> {
                JSONObject memoryObj = obj.getJSONObject("memory");
                return new SemanticMemoryAction(ActionType.AddMemory,
                        SemanticMemoryRecord.fromJson(memoryObj));
            }
            case "UpdateMemory" -> {
                JSONObject updatedMemoryObj = obj.getJSONObject("updated_memory");
                String updatedMemoryId = updatedMemoryObj.getString("update_id");
                UUID updatedMemoryUUID = UUID.fromString(updatedMemoryId);
                return new SemanticMemoryAction(ActionType.UpdateMemory,
                        SemanticMemoryRecord.fromJson(updatedMemoryObj, updatedMemoryUUID));
            }
            case "SkipMemory" -> {
                JSONObject skippedMemoryObj = obj.getJSONObject("memory");
                return new SemanticMemoryAction(ActionType.SkipMemory,
                        SemanticMemoryRecord.fromJson(skippedMemoryObj));
            }
            default -> throw new JSONException("Unknown operation: " + operation);
        }
    }
}