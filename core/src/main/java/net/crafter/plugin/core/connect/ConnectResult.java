package net.crafter.plugin.core.connect;

/** POST /plugin/v2/connect yanıtı. {@link #toString()} token'ı yazmaz. */
public final class ConnectResult {
    private final String websiteId;
    private final String serverId;
    private final String serverToken;
    private final String identityType;
    private final String storeName;

    public ConnectResult(String websiteId, String serverId, String serverToken, String identityType, String storeName) {
        this.websiteId = websiteId;
        this.serverId = serverId;
        this.serverToken = serverToken;
        this.identityType = identityType;
        this.storeName = storeName;
    }

    public String websiteId() { return websiteId; }
    public String serverId() { return serverId; }
    public String serverToken() { return serverToken; }
    public String identityType() { return identityType; }
    public String storeName() { return storeName; }

    @Override
    public String toString() {
        return "ConnectResult{websiteId=" + websiteId + ", serverId=" + serverId + ", token=cct_***}";
    }
}
