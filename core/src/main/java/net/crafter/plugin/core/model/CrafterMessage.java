package net.crafter.plugin.core.model;

import com.google.gson.JsonObject;

/**
 * All WebSocket messages between crafter.net.tr and the plugin
 * are sent/received with this wrapper.
 *
 * Example JSON:
 * {
 *   "type": "COMMAND",
 *   "payload": {
 *     "commandId": "abc123",
 *     "command": "give %player% diamond 1",
 *     "player": "Notch",
 *     "serverId": "survival-1"
 *   }
 * }
 */
public class CrafterMessage {

    @com.google.gson.annotations.SerializedName("type")
    private String type;
    
    private JsonObject payload;
    private JsonObject data; // For statistics type
    private String command; // New simple command format support
    /** Error code in a type:"error" message (server_token_invalid, LICENSE_ERROR …) */
    private String code;
    private String message;

    public CrafterMessage() {}

    public CrafterMessage(MessageType type, JsonObject payload) {
        this.type = type.name();
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTypeString() {
        return type;
    }

    public JsonObject getPayload() {
        return payload;
    }

    public void setPayload(JsonObject payload) {
        this.payload = payload;
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /** Safe string read from the payload */
    public String getPayloadString(String key) {
        if (payload == null || !payload.has(key)) return null;
        return payload.get(key).getAsString();
    }

    /** Safe int read from the payload */
    public int getPayloadInt(String key, int defaultVal) {
        if (payload == null || !payload.has(key)) return defaultVal;
        return payload.get(key).getAsInt();
    }

    @Override
    public String toString() {
        return "CrafterMessage{type=" + getTypeString() + "}";
    }
}

