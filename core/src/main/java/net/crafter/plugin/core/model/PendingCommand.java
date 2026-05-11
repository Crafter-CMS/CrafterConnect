package net.crafter.plugin.core.model;

/**
 * crafter.net.tr'den gelen bir komut paketi.
 * MongoDB'deki dökümanla birebir eşleşir.
 */
public class PendingCommand {

    /** MongoDB _id (ObjectId string) — ACK/NACK için kullanılır */
    private String commandId;

    /** Çalıştırılacak komut. %player% placeholder'ı desteklenir. */
    private String command;

    /** Hedef oyuncunun Minecraft kullanıcı adı */
    private String player;

    /** Hangi sunucuda çalıştırılacak (opsiyonel, boşsa her sunucuda dene) */
    private String serverId;

    /**
     * Oyuncu offline ise ne yapılacak:
     *   QUEUE  → Oyuncu girince tekrar dene (default)
     *   IGNORE → Atla
     */
    private String offlineAction;

    /** Satın alınan ürün adı (loglama/debug için) */
    private String productName;

    public PendingCommand() {
        this.offlineAction = "QUEUE";
    }

    // --- Getters & Setters ---

    public String getCommandId() { return commandId; }
    public void setCommandId(String commandId) { this.commandId = commandId; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getPlayer() { return player; }
    public void setPlayer(String player) { this.player = player; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getOfflineAction() { return offlineAction; }
    public void setOfflineAction(String offlineAction) { this.offlineAction = offlineAction; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    /**
     * Komuttaki %player% placeholder'ını gerçek oyuncu adıyla değiştirir.
     */
    public String resolveCommand() {
        if (command == null) return null;
        return command.replace("%player%", player != null ? player : "");
    }

    @Override
    public String toString() {
        return "PendingCommand{id=" + commandId + ", player=" + player
                + ", product=" + productName + ", cmd=" + command + "}";
    }
}
