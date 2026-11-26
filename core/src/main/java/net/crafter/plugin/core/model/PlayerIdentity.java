package net.crafter.plugin.core.model;

/**
 * Komuttaki ya da olaydaki oyuncu kimliği (#37 bölüm 4): {@code {"type":"minecraft_java","value":"Notch"}}.
 * Minecraft türleri: minecraft_java (kullanıcı adı), minecraft_bedrock (Floodgate önekli ad),
 * minecraft_uuid (Microsoft ile doğrulanmış oyuncunun UUID'si, #378).
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
