package net.crafter.plugin.core.config;

/**
 * config.yml'dan okunan ayarlar.
 * Hem Spigot hem Velocity aynı alanları kullanır.
 */
public class CrafterConfig {

    /** API Base URL (örn: localhost veya api.crafter.net.tr) */
    private String apiUrl = "localhost:3000";

    /** Website ID (dashboard'dan alınır) */
    private String websiteId = "YOUR_WEBSITE_ID";

    /** Plugin Secret (dashboard'dan alınan güvenlik anahtarı) */
    private String pluginSecret = "YOUR_SECRET";

    /** Bu sunucunun benzersiz ID'si (dashboard ile eşleşmeli) */
    private String serverId = "my-server-1";

    /** Eklenti dili (tr, en, vb.) */
    private String language = "tr";

    /** Bağlantı kopunca kaç saniye sonra tekrar bağlanmayı denesin */
    private int reconnectDelaySeconds = 5;

    /** Maksimum reconnect denemesi (-1 = sonsuz) */
    private int maxReconnectAttempts = -1;

    /** Ping interval (saniye) — bağlantının canlı olduğunu kontrol eder */
    private int pingIntervalSeconds = 30;

    /** Komut loglarını konsola yaz */
    private boolean debug = false;

    // --- Getters & Setters ---

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getWebsiteId() { return websiteId; }
    public void setWebsiteId(String websiteId) { this.websiteId = websiteId; }

    public String getPluginSecret() { return pluginSecret; }
    public void setPluginSecret(String pluginSecret) { this.pluginSecret = pluginSecret; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public int getReconnectDelaySeconds() { return reconnectDelaySeconds; }
    public void setReconnectDelaySeconds(int reconnectDelaySeconds) { this.reconnectDelaySeconds = reconnectDelaySeconds; }

    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }

    public int getPingIntervalSeconds() { return pingIntervalSeconds; }
    public void setPingIntervalSeconds(int pingIntervalSeconds) { this.pingIntervalSeconds = pingIntervalSeconds; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }
}

