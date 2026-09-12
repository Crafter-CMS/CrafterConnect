package net.crafter.plugin.core.config;

/**
 * config.yml'dan okunan ayarlar.
 * Hem Spigot hem Velocity aynı alanları kullanır.
 */
public class CrafterConfig {

    public static final String DEFAULT_WEBSITE_ID = "YOUR_WEBSITE_ID";
    public static final String DEFAULT_PLUGIN_SECRET = "YOUR_PLUGIN_SECRET";
    public static final String DEFAULT_SERVER_ID = "YOUR_SERVER_ID";

    /** API Base URL (örn: localhost veya api.crafter.net.tr) */
    private String apiUrl = "api.crafter.net.tr";

    /** Website ID (dashboard'dan alınır) */
    private String websiteId = DEFAULT_WEBSITE_ID;

    /** Plugin Secret (dashboard'dan alınan güvenlik anahtarı) */
    private String pluginSecret = DEFAULT_PLUGIN_SECRET;

    /** Bu sunucunun benzersiz ID'si (dashboard ile eşleşmeli) */
    private String serverId = DEFAULT_SERVER_ID;

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

    /** SSL/TLS kullanılsın mı? (wss://) */
    private boolean useSsl = true;

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

    public boolean isUseSsl() { return useSsl; }
    public void setUseSsl(boolean useSsl) { this.useSsl = useSsl; }

    /**
     * Yapılandırmanın geçerli (kullanıcı tarafından girilmiş) olup olmadığını kontrol eder.
     * Varsayılan yer tutucular ("YOUR_...") veya boş değerler varsa false döner.
     */
    public boolean isConfigured() {
        return isValidValue(websiteId, DEFAULT_WEBSITE_ID)
                && isValidValue(pluginSecret, DEFAULT_PLUGIN_SECRET, "YOUR_SECRET", "YOUR_SECRET_KEY")
                && isValidValue(serverId, DEFAULT_SERVER_ID, "my-server-1", "my-proxy-1");
    }

    private static boolean isValidValue(String val, String defaultVal, String... legacyDefaults) {
        if (val == null) return false;
        String trimmed = val.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.equalsIgnoreCase(defaultVal)) return false;
        if (trimmed.toUpperCase().startsWith("YOUR_")) return false;
        for (String legacy : legacyDefaults) {
            if (trimmed.equalsIgnoreCase(legacy)) return false;
        }
        return true;
    }
}

