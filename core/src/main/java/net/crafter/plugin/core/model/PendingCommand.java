package net.crafter.plugin.core.model;

/**
 * Backend'den gelen teslimat komutu ({@code {"type":"command","payload":{…}}}).
 * <pre>{"commandId":"cmd_…","command":"lp user Notch parent add vip","player":"Notch",
 *  "identity":{"type":"minecraft_uuid","value":"…"},"requireOnline":null}</pre>
 * Backend %username% gibi yer tutucuları kendisi doldurur; eklenti yalnızca {@code %player%}'ı doldurur.
 */
public class PendingCommand {

    public static final String PLAYER_PLACEHOLDER = "%player%";

    private String commandId;
    private String command;
    private String player;
    private PlayerIdentity identity;
    /** null: oyuncu varsa çevrimiçi olmasını bekle; false: hemen çalıştır; true: bekle */
    private Boolean requireOnline;
    /** Eklenti kuyruğuna alındığı an (epoch ms); yalnızca yerel */
    private long queuedAt;

    public PendingCommand() {}

    public PendingCommand(String commandId, String command, String player) {
        this.commandId = commandId;
        this.command = command;
        this.player = player;
    }

    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getPlayer() { return player; }
    public void setPlayer(String player) { this.player = player; }

    public PlayerIdentity getIdentity() { return identity; }
    public void setIdentity(PlayerIdentity identity) { this.identity = identity; }

    public Boolean getRequireOnline() { return requireOnline; }
    public void setRequireOnline(Boolean requireOnline) { this.requireOnline = requireOnline; }

    public long getQueuedAt() { return queuedAt; }
    public void setQueuedAt(long queuedAt) { this.queuedAt = queuedAt; }

    public boolean hasPlayer() {
        return player != null && !player.isBlank();
    }

    /** Komut oyuncunun çevrimiçi olmasını bekler mi */
    public boolean waitsForPlayer() {
        return hasPlayer() && !Boolean.FALSE.equals(requireOnline);
    }

    /** Doğrulanmış UUID (identity minecraft_uuid ise), yoksa null */
    public String verifiedUuid() {
        if (identity == null || !PlayerIdentity.MINECRAFT_UUID.equals(identity.getType())) return null;
        return identity.getValue();
    }

    /**
     * Konsola gidecek komut: baştaki "/" atılır ve %player% oyuncu adıyla doldurulur. Oyuncu adı önce
     * {@link net.crafter.plugin.core.delivery.CommandGuard} ile doğrulanmalıdır.
     */
    public String resolveCommand() {
        if (command == null) return null;
        String resolved = command.trim();
        while (resolved.startsWith("/")) resolved = resolved.substring(1).trim();
        return resolved.replace(PLAYER_PLACEHOLDER, commandPlayer());
    }

    /** %player% değeri: boşluklu (Bedrock) ad çift tırnak içinde tek argüman olur (backend commandUsername ile aynı) */
    public String commandPlayer() {
        String p = player == null ? "" : player.trim();
        return p.contains(" ") ? "\"" + p + "\"" : p;
    }

    @Override
    public String toString() {
        // Komut metni ve oyuncu adı loglarda gösterilmez
        return "PendingCommand{id=" + commandId + "}";
    }
}
