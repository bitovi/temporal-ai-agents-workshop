package bitovi.common.local.types;

import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

public record UserPreferenceMemoryActions(ActionType operation,
        UserPreferenceMemoryRecord memory) {

    private enum ActionType {
        AddMemory,
        UpdateMemory,
        SkipMemory
    }

    public static UserPreferenceMemoryActions fromJson(JSONObject obj) throws JSONException {
        String operation = obj.getString("operation");
        switch (operation) {
            case "AddMemory" -> {
                JSONObject memoryObj = obj.getJSONObject("memory");
                return new UserPreferenceMemoryActions(ActionType.AddMemory,
                        UserPreferenceMemoryRecord.fromJson(memoryObj));
            }
            case "UpdateMemory" -> {
                JSONObject updatedMemoryObj = obj.getJSONObject("updated_memory");
                String updatedMemoryId = updatedMemoryObj.getString("update_id");
                UUID updatedMemoryUUID = UUID.fromString(updatedMemoryId);
                return new UserPreferenceMemoryActions(ActionType.UpdateMemory,
                        UserPreferenceMemoryRecord.fromJson(updatedMemoryObj, updatedMemoryUUID));
            }
            case "SkipMemory" -> {
                JSONObject skippedMemoryObj = obj.getJSONObject("memory");
                return new UserPreferenceMemoryActions(ActionType.SkipMemory,
                        UserPreferenceMemoryRecord.fromJson(skippedMemoryObj));
            }
            default -> throw new JSONException("Unknown operation: " + operation);
        }
    }
}
