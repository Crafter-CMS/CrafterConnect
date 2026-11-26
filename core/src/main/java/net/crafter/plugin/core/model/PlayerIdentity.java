package net.crafter.plugin.core.model;

/**
 * Player identity in a command or event (#37 section 4): {@code {"type":"minecraft_java","value":"Notch"}}.
 * Minecraft types: minecraft_java (username), minecraft_bedrock (Floodgate-prefixed name),
 * minecraft_uuid (UUID of a Microsoft-verified player, #378).
 */
public class PlayerIdentity {

    public static final String MINECRAFT_JAVA = "minecraft_java";
    public static final String MINECRAFT_BEDROCK = "minecraft_bedrock";
    public static final String MINECRAFT_UUID = "minecraft_uuid";

    private String type;
    private String value;

    public PlayerIdentity() {}

    public PlayerIdentity(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() { return type; }
    public String getValue() { return value; }

    @Override
    public String toString() {
        return type + ":" + value;
    }
}
