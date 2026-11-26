package net.crafter.plugin.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * config.yml ayarları (CrafterConnect 2.0, #371). Paper/Spigot, Velocity ve BungeeCord aynı alanları kullanır.
 *
 * Bağlantı iki yoldan biriyle kurulur:
 * <ul>
 *   <li><b>Sunucu token'ı</b> ({@code server-token}, {@code cct_…}): {@code /crafter connect <kod>} yazar; elle girilmez.</li>
 *   <li><b>Eski bağlantı</b> ({@code website-id} + {@code plugin-secret} + {@code server-id}): 2.0 öncesi kurulumlar
 *   güncellemeden sonra da çalışır; token boşsa kullanılır ve konsola yeniden bağlama uyarısı yazılır.</li>
 * </ul>
 * {@link #toString()} gizli değerleri yazmaz.
 */
public class CrafterConfig {

    public static final String DEFAULT_WEBSITE_ID = "YOUR_WEBSITE_ID";
    public static final String DEFAULT_PLUGIN_SECRET = "YOUR_PLUGIN_SECRET";
    public static final String DEFAULT_SERVER_ID = "YOUR_SERVER_ID";
    public static final String DEFAULT_API_URL = "api.crafter.net.tr";

    /** Bağlantı biçimi */
    public enum Mode { TOKEN, LEGACY, NONE }

    private String apiUrl = DEFAULT_API_URL;
    private boolean useSsl = true;
    private String serverToken = "";
    private String websiteId = DEFAULT_WEBSITE_ID;
    private String pluginSecret = DEFAULT_PLUGIN_SECRET;
    private String serverId = DEFAULT_SERVER_ID;
    private String language = "tr";
    private int reconnectDelaySeconds = 5;
    private int maxReconnectAttempts = -1;
    private boolean debug = false;
    private boolean reportPlayers = true;
    private String bedrockPlayerPrefix = "";
    private List<String> allowedCommandPrefixes = new ArrayList<>();
    private boolean playtimeEnabled = true;
    private int playtimeSampleMinutes = 5;
    private String afkSource = "auto";
    private int afkMinutes = 5;
    private boolean playtimeImportHistory = false;

    /** config.yml'dan okur; eksik alanlarda varsayılan kullanılır */
    public static CrafterConfig from(ConfigSource src) {
        CrafterConfig cfg = new CrafterConfig();
        cfg.setApiUrl(src.getString("api-url", DEFAULT_API_URL));
        cfg.setUseSsl(src.getBoolean("use-ssl", true));
        cfg.setServerToken(src.getString("server-token", ""));
        cfg.setWebsiteId(src.getString("website-id", DEFAULT_WEBSITE_ID));
        cfg.setPluginSecret(src.getString("plugin-secret", DEFAULT_PLUGIN_SECRET));
        cfg.setServerId(src.getString("server-id", DEFAULT_SERVER_ID));
        cfg.setLanguage(src.getString("language", "tr"));
        cfg.setReconnectDelaySeconds(src.getInt("reconnect-delay-seconds", 5));
        cfg.setMaxReconnectAttempts(src.getInt("max-reconnect-attempts", -1));
        // 1.x Spigot "debug-mode", Velocity "debug" kullanıyordu
        cfg.setDebug(src.getBoolean("debug-mode", src.getBoolean("debug", false)));
        cfg.setReportPlayers(src.getBoolean("report-players", true));
        cfg.setBedrockPlayerPrefix(src.getString("bedrock-player-prefix", ""));
        cfg.setAllowedCommandPrefixes(src.getStringList("allowed-command-prefixes"));
        cfg.setPlaytimeEnabled(src.getBoolean("playtime.enabled", true));
        cfg.setPlaytimeSampleMinutes(src.getInt("playtime.sample-minutes", 5));
        cfg.setAfkSource(src.getString("playtime.afk-source", "auto"));
        cfg.setAfkMinutes(src.getInt("playtime.afk-minutes", 5));
        cfg.setPlaytimeImportHistory(src.getBoolean("playtime.import-history", false));
        return cfg;
    }

    // --- Bağlantı ---

    public Mode mode() {
        if (hasServerToken()) return Mode.TOKEN;
        if (isLegacyConfigured()) return Mode.LEGACY;
        return Mode.NONE;
    }

    public boolean hasServerToken() {
        return looksLikeServerToken(serverToken);
    }

    /** Backend'in ürettiği biçim: "cct_" + base64url */
    public static boolean looksLikeServerToken(String value) {
        return value != null && value.matches("^cct_[A-Za-z0-9_-]{20,100}$");
    }

    /** Herhangi bir bağlantı bilgisi var mı (token ya da eski alanlar) */
    public boolean isConfigured() {
        return mode() != Mode.NONE;
    }

    /** Eski alanlar dolu mu (varsayılan yer tutucular sayılmaz) */
    public boolean isLegacyConfigured() {
        return isValidValue(websiteId, DEFAULT_WEBSITE_ID)
                && isValidValue(pluginSecret, DEFAULT_PLUGIN_SECRET, "YOUR_SECRET", "YOUR_SECRET_KEY")
                && isValidValue(serverId, DEFAULT_SERVER_ID, "my-server-1", "my-proxy-1");
    }

    /** API ana adresi: https://api.crafter.net.tr (api-url şemalı ya da şemasız yazılabilir) */
    public String httpBase() {
        String host = apiHost();
        boolean ssl = useSsl;
        String raw = apiUrl == null ? "" : apiUrl.trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("https://") || raw.startsWith("wss://")) ssl = true;
        else if (raw.startsWith("http://") || raw.startsWith("ws://")) ssl = false;
        return (ssl ? "https://" : "http://") + host;
    }

    /** WebSocket ana adresi: wss://api.crafter.net.tr */
    public String wsBase() {
        String http = httpBase();
        return http.startsWith("https://") ? "wss://" + http.substring(8) : "ws://" + http.substring(7);
    }

    /** api-url'den yalnızca host[:port][/yol] (şema ve sondaki / atılır) */
    public String apiHost() {
        String raw = apiUrl == null || apiUrl.isBlank() ? DEFAULT_API_URL : apiUrl.trim();
        raw = raw.replaceFirst("(?i)^(https?|wss?)://", "");
        while (raw.endsWith("/")) raw = raw.substring(0, raw.length() - 1);
        return raw.isEmpty() ? DEFAULT_API_URL : raw;
    }

    // --- Getters & Setters ---

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl == null ? DEFAULT_API_URL : apiUrl; }

    public boolean isUseSsl() { return useSsl; }
    public void setUseSsl(boolean useSsl) { this.useSsl = useSsl; }

    public String getServerToken() { return serverToken; }
    public void setServerToken(String serverToken) { this.serverToken = serverToken == null ? "" : serverToken.trim(); }

    public String getWebsiteId() { return websiteId; }
    public void setWebsiteId(String websiteId) { this.websiteId = websiteId; }

    public String getPluginSecret() { return pluginSecret; }
    public void setPluginSecret(String pluginSecret) { this.pluginSecret = pluginSecret; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) {
        String v = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        // Dil dosyası adı olarak kullanılır: yol karakterleri kabul edilmez
        this.language = v.matches("^[a-z]{2}(_[a-z]{2})?$") ? v : "tr";
    }

    public int getReconnectDelaySeconds() { return reconnectDelaySeconds; }
    public void setReconnectDelaySeconds(int reconnectDelaySeconds) { this.reconnectDelaySeconds = Math.max(1, reconnectDelaySeconds); }

    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public void setMaxReconnectAttempts(int maxReconnectAttempts) { this.maxReconnectAttempts = maxReconnectAttempts; }

    public boolean isDebug() { return debug; }
    public void setDebug(boolean debug) { this.debug = debug; }

    public boolean isReportPlayers() { return reportPlayers; }
    public void setReportPlayers(boolean reportPlayers) { this.reportPlayers = reportPlayers; }

    public String getBedrockPlayerPrefix() { return bedrockPlayerPrefix; }
    public void setBedrockPlayerPrefix(String bedrockPlayerPrefix) {
        this.bedrockPlayerPrefix = bedrockPlayerPrefix == null ? "" : bedrockPlayerPrefix.trim();
    }

    public List<String> getAllowedCommandPrefixes() { return Collections.unmodifiableList(allowedCommandPrefixes); }
    public void setAllowedCommandPrefixes(List<String> allowedCommandPrefixes) {
        this.allowedCommandPrefixes = allowedCommandPrefixes == null ? new ArrayList<>() : new ArrayList<>(allowedCommandPrefixes);
    }

    // --- Oynama süresi (#529, yalnızca Paper/Spigot) ---

    public boolean isPlaytimeEnabled() { return playtimeEnabled; }
    public void setPlaytimeEnabled(boolean playtimeEnabled) { this.playtimeEnabled = playtimeEnabled; }

    /** 1–60 dk */
    public int getPlaytimeSampleMinutes() { return playtimeSampleMinutes; }
    public void setPlaytimeSampleMinutes(int minutes) { this.playtimeSampleMinutes = Math.max(1, Math.min(60, minutes)); }

    /** auto | essentials | cmi | idle | none */
    public String getAfkSource() { return afkSource; }
    public void setAfkSource(String afkSource) {
        String v = afkSource == null ? "" : afkSource.trim().toLowerCase(Locale.ROOT);
        this.afkSource = List.of("auto", "essentials", "cmi", "idle", "none").contains(v) ? v : "auto";
    }

    /** Hareketsizlik eşiği, 1–120 dk */
    public int getAfkMinutes() { return afkMinutes; }
    public void setAfkMinutes(int minutes) { this.afkMinutes = Math.max(1, Math.min(120, minutes)); }

    public boolean isPlaytimeImportHistory() { return playtimeImportHistory; }
    public void setPlaytimeImportHistory(boolean v) { this.playtimeImportHistory = v; }

    private static boolean isValidValue(String val, String defaultVal, String... legacyDefaults) {
        if (val == null) return false;
        String trimmed = val.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.equalsIgnoreCase(defaultVal)) return false;
        if (trimmed.toUpperCase(Locale.ROOT).startsWith("YOUR_")) return false;
        for (String legacy : legacyDefaults) {
            if (trimmed.equalsIgnoreCase(legacy)) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "CrafterConfig{api=" + httpBase() + ", mode=" + mode() + ", language=" + language + "}";
    }
}
