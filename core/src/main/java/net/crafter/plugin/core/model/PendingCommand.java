package net.crafter.plugin.core.model;

/**
 * Delivery command from the backend ({@code {"type":"command","payload":{…}}}).
 * <pre>{"commandId":"cmd_…","command":"lp user Notch parent add vip","player":"Notch",
 *  "identity":{"type":"minecraft_uuid","value":"…"},"requireOnline":null}</pre>
 * The backend fills placeholders such as %username% itself; the plugin only fills {@code %player%}.
 */
public class PendingCommand {

    public static final String PLAYER_PLACEHOLDER = "%player%";

    private String commandId;
    private String command;
    private String player;
    private PlayerIdentity identity;
    /** null: wait for the player to be online if there is a player; false: run now; true: wait */
    private Boolean requireOnline;
    /** When it was added to the plugin queue (epoch ms); local only */
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

    /** Whether the command waits for the player to be online */
    public boolean waitsForPlayer() {
        return hasPlayer() && !Boolean.FALSE.equals(requireOnline);
    }

    /** Verified UUID (if identity is minecraft_uuid), otherwise null */
    public String verifiedUuid() {
        if (identity == null || !PlayerIdentity.MINECRAFT_UUID.equals(identity.getType())) return null;
        return identity.getValue();
    }

    /**
     * Command sent to the console: the leading "/" is dropped and %player% is filled with the player name. The player name
     * must be validated first with {@link net.crafter.plugin.core.delivery.CommandGuard}.
     */
    public String resolveCommand() {
        if (command == null) return null;
        String resolved = command.trim();
        while (resolved.startsWith("/")) resolved = resolved.substring(1).trim();
        return resolved.replace(PLAYER_PLACEHOLDER, commandPlayer());
    }

    /** %player% value: a spaced (Bedrock) name becomes a single double-quoted argument (same as backend commandUsername) */
    public String commandPlayer() {
        String p = player == null ? "" : player.trim();
        return p.contains(" ") ? "\"" + p + "\"" : p;
    }

    @Override
    public String toString() {
        // The command text and player name are never shown in logs
        return "PendingCommand{id=" + commandId + "}";
    }
}
