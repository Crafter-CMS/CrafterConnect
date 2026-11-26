package net.crafter.plugin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.crafter.plugin.core.config.ConfigFileEditor;
import net.crafter.plugin.core.config.CrafterConfig;
import net.crafter.plugin.core.config.SecureFiles;
import net.crafter.plugin.core.connect.ConnectClient;
import net.crafter.plugin.core.connect.ConnectException;
import net.crafter.plugin.core.connect.ConnectResult;
import net.crafter.plugin.core.connect.HttpTransport;
import net.crafter.plugin.core.connect.ServerInfo;
import net.crafter.plugin.core.delivery.CommandGuard;
import net.crafter.plugin.core.delivery.CommandProcessor;
import net.crafter.plugin.core.delivery.CommandStore;
import net.crafter.plugin.core.model.PlayerIdentity;
import net.crafter.plugin.core.platform.CrafterLog;
import net.crafter.plugin.core.platform.FloodgateBridge;
import net.crafter.plugin.core.platform.Platform;
import net.crafter.plugin.core.playtime.AfkSource;
import net.crafter.plugin.core.playtime.PlayerSnapshot;
import net.crafter.plugin.core.playtime.PlaytimeReporter;
import net.crafter.plugin.core.playtime.PlaytimeStore;
import net.crafter.plugin.core.websocket.CrafterWebSocketClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Platform-independent core of CrafterConnect: config, language, persistent queue, connection and
 * {@code /crafter connect | disconnect | status | reload}. The Paper/Spigot, Velocity and BungeeCord modules only
 * forward events and commands here.
 */
public class CrafterConnectService {

    static final String TOKEN_COMMENT = "Server token (cct_...): written by /crafter connect.\n"
            + "Written by /crafter connect. Do not edit or share.";

    private final Platform platform;
    private final CrafterLog log;
    private final StatisticsManager statsManager = new StatisticsManager();
    private final PlayerManager playerManager = new PlayerManager();
    private final MarketManager marketManager = new MarketManager();
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HttpTransport http;

    private volatile CrafterConfig config = new CrafterConfig();
    private volatile LanguageManager lang = new LanguageManager("tr");
    private volatile CrafterWebSocketClient wsClient;
    private volatile CrafterWebSocketClient.AuthInfo auth;
    private volatile String minVersion;
    private volatile UpdateCheckResult updateResult;
    private volatile ConnectionInfo connectionInfo;
    private volatile FloodgateBridge floodgate;
    private CommandStore store;
    private CommandProcessor processor;
    private ScheduledExecutorService io;
    /** Playtime (#529); null on proxies and when playtime.enabled is false */
    private volatile PlaytimeReporter playtime;
    private volatile AfkSource afkSource;
    private volatile ScheduledFuture<?> sampleTask;
    private volatile ScheduledFuture<?> afkTask;

    static final long AFK_POLL_SECONDS = 30;
    /** Scheduler unit: the "minutes" in the config (tests shorten it) */
    long playtimeMinuteMs = 60_000;
    long afkPollMs = AFK_POLL_SECONDS * 1000;

    /** data/connection.json: non-secret connection info (store name, server id) */
    static final class ConnectionInfo {
        String websiteId;
        String serverId;
        String storeName;
        long linkedAt;
    }

    public CrafterConnectService(Platform platform) {
        this(platform, HttpTransport.standard("CrafterConnect/" + BuildInfo.version()));
    }

    public CrafterConnectService(Platform platform, HttpTransport http) {
        this.platform = platform;
        this.log = platform.log();
        this.http = http;
    }

    // --- Lifecycle ---

    public synchronized void start() {
        if (io == null || io.isShutdown()) {
            io = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "crafter-io");
                t.setDaemon(true);
                return t;
            });
            io.scheduleWithFixedDelay(this::runUpdateCheck, 5, 6 * 60 * 60, TimeUnit.SECONDS);
        }
        loadSettings();
        floodgate = platform.floodgate();
        if (store == null) {
            Path data = dataDir();
            store = new CommandStore(data.resolve("commands.json"),
                    platform.dataDirectory().resolve("pending-commands.json"), System::currentTimeMillis, log);
            processor = new CommandProcessor(store, platform, this::guard, log);
        }
        connectionInfo = readConnectionInfo();
        startPlaytime();
        startClient();
    }

    public synchronized void stop() {
        stopPlaytime();
        stopClient();
        if (io != null) {
            io.shutdownNow();
            io = null;
        }
    }

    /** /crafter reload: re-reads the config and language file and reconnects */
    public synchronized void reload() {
        stopClient();
        stopPlaytime();
        loadSettings();
        startPlaytime();
        startClient();
    }

    // --- Playtime (#529) ---

    /** Paper/Spigot: a stat sample every 5 minutes (configurable), an AFK poll every 30 seconds */
    private void startPlaytime() {
        if (!platform.supportsPlayerStats() || !config.isPlaytimeEnabled() || io == null) return;
        if (playtime == null) {
            PlaytimeStore ps = new PlaytimeStore(dataDir().resolve("playtime.json"), log);
            playtime = new PlaytimeReporter(ps, afkPollMs, System::currentTimeMillis, log);
        }
        playtime.setImportHistory(config.isPlaytimeImportHistory());
        try {
            afkSource = platform.afkSource(config.getAfkSource(), config.getAfkMinutes() * 60_000L);
        } catch (RuntimeException e) {
            log.warn("[CrafterConnect] AFK detection unavailable: " + e.getMessage());
            afkSource = null;
        }
        long sample = config.getPlaytimeSampleMinutes() * playtimeMinuteMs;
        sampleTask = io.scheduleWithFixedDelay(this::samplePlaytime, sample, sample, TimeUnit.MILLISECONDS);
        afkTask = io.scheduleWithFixedDelay(this::pollAfk, afkPollMs, afkPollMs, TimeUnit.MILLISECONDS);
    }

    private void stopPlaytime() {
        if (sampleTask != null) sampleTask.cancel(false);
        if (afkTask != null) afkTask.cancel(false);
        sampleTask = null;
        afkTask = null;
    }

    private void samplePlaytime() {
        PlaytimeReporter r = playtime;
        if (r == null) return;
        try {
            platform.collectPlayerStats(list -> {
                r.record(list, "interval");
                flushPlaytime();
            });
        } catch (Throwable t) {
            log.debug("[CrafterConnect] Playtime sample failed: " + t.getMessage());
        }
    }

    private void pollAfk() {
        PlaytimeReporter r = playtime;
        if (r == null) return;
        try {
            r.pollAfk(platform.onlinePlayerIds(), afkSource);
        } catch (Throwable t) {
            log.debug("[CrafterConnect] AFK poll failed: " + t.getMessage());
        }
    }

    /**
     * Called from the player's thread on join and quit (stats are read there); disk and network work runs on the io thread.
     * The backend is order-independent: even if the quit sample arrives after player_left, time is recorded once.
     */
    public void recordPlayerStats(PlayerSnapshot snapshot, String reason) {
        PlaytimeReporter r = playtime;
        ScheduledExecutorService exec = io;
        if (r == null || snapshot == null || sampleTask == null || exec == null) return;
        try {
            exec.execute(() -> {
                r.record(List.of(snapshot), reason);
                flushPlaytime();
            });
        } catch (RejectedExecutionException e) {
            // during shutdown: the sample is written straight to the queue and sent on the next startup
            r.record(List.of(snapshot), reason);
        }
    }

    /** On shutdown, from the main thread: final values are written to the queue synchronously and sent on the next startup */
    public void recordShutdown(List<PlayerSnapshot> snapshots) {
        PlaytimeReporter r = playtime;
        if (r == null || snapshots == null || snapshots.isEmpty() || sampleTask == null) return;
        try {
            r.record(snapshots, "quit");
        } catch (RuntimeException e) {
            log.debug("[CrafterConnect] Playtime shutdown sample failed: " + e.getMessage());
        }
    }

    private void flushPlaytime() {
        PlaytimeReporter r = playtime;
        CrafterWebSocketClient c = wsClient;
        if (r == null || c == null) return;
        r.flush(senderOf(c));
    }

    private void loadSettings() {
        try {
            config = CrafterConfig.from(platform.loadConfig());
        } catch (IOException e) {
            log.error("[CrafterConnect] Could not read config.yml: " + e.getMessage());
            config = new CrafterConfig();
        }
        try {
            Map<String, String> messages = platform.loadMessages(config.getLanguage());
            LanguageManager lm = new LanguageManager(config.getLanguage());
            lm.setMessages(messages);
            lang = lm;
        } catch (IOException e) {
            log.error("[CrafterConnect] Could not read language file: " + e.getMessage());
        }
        if (config.hasServerToken() && SecureFiles.isReadableByOthers(platform.configFile())) {
            // A file holding the token must not be readable by other users
            if (SecureFiles.restrictFile(platform.configFile())) {
                log.warn("[CrafterConnect] config.yml permissions were restricted to the server user (it holds the server token).");
            }
        }
    }

    private void startClient() {
        auth = null;
        switch (config.mode()) {
            case NONE -> console("console_not_connected");
            case LEGACY -> console("console_legacy_warning");
            case TOKEN -> { }
        }
        if (!config.isConfigured()) return;
        CrafterWebSocketClient client = new CrafterWebSocketClient(config, platform, processor, statsManager,
                playerManager, marketManager, () -> lang, this::serverInfo, new CrafterWebSocketClient.Listener() {
                    @Override public void onAuthenticated(CrafterWebSocketClient.AuthInfo info) {
                        auth = info;
                        checkMinVersionAsync(info.websiteId() != null ? info.websiteId() : config.getWebsiteId());
                        PlaytimeReporter r = playtime;
                        if (r != null) {
                            r.onConnected();
                            r.flush(senderOf(wsClient));
                        }
                    }
                    @Override public void onPlayerStatsAck(List<String> keys) {
                        PlaytimeReporter r = playtime;
                        if (r != null) r.onAck(keys, senderOf(wsClient));
                    }
                    @Override public void onTokenRejected(String reason) {
                        console("console_token_rejected");
                    }
                });
        wsClient = client;
        client.start();
    }

    /** Sender that rejects every send when there is no connection */
    private PlaytimeReporter.Sender senderOf(CrafterWebSocketClient c) {
        return c == null ? data -> false : c::sendPlayerStats;
    }

    private void stopClient() {
        CrafterWebSocketClient c = wsClient;
        wsClient = null;
        auth = null;
        if (c != null) c.shutdown();
    }

    // --- /crafter connect ---

    /**
     * Redeems the link code (HTTP request on a separate thread). On success the token is written to config.yml with
     * 600 permissions and the connection is re-established with the token. Legacy connection fields stay in the file but are unused.
     */
    public void connect(String rawCode, Consumer<String> reply) {
        if (!connecting.compareAndSet(false, true)) {
            reply.accept(msg("connect_in_progress"));
            return;
        }
        reply.accept(msg("connect_started"));
        ScheduledExecutorService exec = io;
        if (exec == null) {
            connecting.set(false);
            return;
        }
        exec.execute(() -> {
            try {
                ConnectClient client = new ConnectClient(config.httpBase(), http);
                ConnectResult result = client.connect(rawCode, serverInfo());
                ConfigFileEditor.setString(platform.configFile(), "server-token", result.serverToken(), TOKEN_COMMENT);
                ConnectionInfo info = new ConnectionInfo();
                info.websiteId = result.websiteId();
                info.serverId = result.serverId();
                info.storeName = result.storeName();
                info.linkedAt = System.currentTimeMillis();
                writeConnectionInfo(info);
                log.info("[CrafterConnect] Server linked (server " + result.serverId() + ").");
                reload();
                reply.accept(msg("connect_success", nonEmpty(result.storeName(), "-"), result.serverId()));
            } catch (ConnectException e) {
                reply.accept(connectError(e));
            } catch (IOException e) {
                log.error("[CrafterConnect] Could not save the server token: " + e.getMessage());
                reply.accept(msg("connect_save_error"));
            } catch (RuntimeException e) {
                log.error("[CrafterConnect] Connect failed: " + e.getMessage());
                reply.accept(msg("connect_error_server_error"));
            } finally {
                connecting.set(false);
            }
        });
    }

    String connectError(ConnectException e) {
        String key = "connect_error_" + e.code();
        String text = lang.has(key) ? lang.getMessage(key) : lang.getMessage("connect_error_server_error");
        if ("plugin_protocol_unsupported".equals(e.code()) && e.detail() != null) {
            text = lang.getMessage("connect_error_plugin_protocol_unsupported", e.detail());
        }
        return lang.getMessage("prefix") + text;
    }

    /** /crafter disconnect: the token is removed and the connection closed (the server stays in the panel; reconnecting needs a new code) */
    public synchronized void disconnect(Consumer<String> reply) {
        if (!config.hasServerToken()) {
            reply.accept(msg("disconnect_not_connected"));
            return;
        }
        stopClient();
        try {
            ConfigFileEditor.setString(platform.configFile(), "server-token", "", TOKEN_COMMENT);
            Files.deleteIfExists(dataDir().resolve("connection.json"));
        } catch (IOException e) {
            log.error("[CrafterConnect] Could not clear the server token: " + e.getMessage());
            reply.accept(msg("connect_save_error"));
            return;
        }
        connectionInfo = null;
        loadSettings();
        reply.accept(msg("disconnect_done"));
    }

    // --- /crafter status ---

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        String p = lang.getMessage("prefix");
        lines.add(p + lang.getMessage("status_title"));
        CrafterWebSocketClient c = wsClient;
        String state;
        if (!config.isConfigured()) state = lang.getMessage("status_not_configured");
        else if (c != null && c.isConnected()) state = lang.getMessage("status_connected");
        else if (c != null && c.isStopped()) state = lang.getMessage("status_token_invalid");
        else state = lang.getMessage("status_disconnected");
        lines.add(p + lang.getMessage("status_info_line", state));
        lines.add(p + lang.getMessage("status_mode_line", lang.getMessage("status_mode_" + config.mode().name().toLowerCase())));
        ConnectionInfo info = connectionInfo;
        CrafterWebSocketClient.AuthInfo a = auth;
        String serverId = a != null && a.serverId() != null ? a.serverId()
                : info != null ? info.serverId
                : config.mode() == CrafterConfig.Mode.LEGACY ? config.getServerId() : "-";
        lines.add(p + lang.getMessage("status_store_line", info != null ? nonEmpty(info.storeName, "-") : "-"));
        lines.add(p + lang.getMessage("status_server_line", serverId));
        lines.add(p + lang.getMessage("status_version_line", BuildInfo.version(), platform.platformId()));
        lines.add(p + lang.getMessage("status_queue_line", store == null ? 0 : store.queuedCount()));
        PlaytimeReporter pt = playtime;
        if (pt != null && pt.queued() > 0) lines.add(p + lang.getMessage("status_playtime_queue_line", pt.queued()));
        if (store != null && store.outgoingCount() > 0) {
            lines.add(p + lang.getMessage("status_outbox_line", store.outgoingCount()));
        }
        if (UpdateChecker.isBelowMinimum(BuildInfo.version(), minVersion)) {
            lines.add(p + lang.getMessage("status_update_required", minVersion));
        } else if (updateResult != null && updateResult.hasUpdate()) {
            lines.add(p + lang.getMessage("version_warning", updateResult.getLatestVersion()));
        }
        return lines;
    }

    // --- Player events ---

    public void onPlayerJoin(String username, UUID uuid) {
        PlayerIdentity identity = identityOf(username, uuid);
        // The queue is processed even without a connection; ACKs wait in the outbox
        if (processor != null) processor.onPlayerJoin(username, uuid);
        CrafterWebSocketClient c = wsClient;
        if (c != null) c.onPlayerJoin(username, identity);
    }

    public void onPlayerQuit(String username, UUID uuid) {
        playerManager.removePlayer(username);
        CrafterWebSocketClient c = wsClient;
        if (c != null) c.onPlayerQuit(username, identityOf(username, uuid));
    }

    public PlayerIdentity identityOf(String username, UUID uuid) {
        FloodgateBridge fg = floodgate;
        boolean bedrock = fg != null && fg.isBedrock(uuid);
        return new PlayerIdentity(bedrock ? PlayerIdentity.MINECRAFT_BEDROCK : PlayerIdentity.MINECRAFT_JAVA, username);
    }

    public void getProducts() {
        CrafterWebSocketClient c = wsClient;
        if (c != null) c.getProducts();
    }

    public void purchaseProduct(String username, UUID uuid, String productId) {
        CrafterWebSocketClient c = wsClient;
        if (c != null) c.purchaseProduct(username, identityOf(username, uuid), productId);
    }

    public void getChest(String username, UUID uuid) {
        CrafterWebSocketClient c = wsClient;
        if (c != null) c.getChest(username, identityOf(username, uuid));
    }

    public void useChestItem(String username, UUID uuid, String chestItemId) {
        CrafterWebSocketClient c = wsClient;
        if (c != null) c.useChestItem(username, identityOf(username, uuid), chestItemId);
    }

    // --- Helpers ---

    CommandGuard guard() {
        CrafterWebSocketClient.AuthInfo a = auth;
        return new CommandGuard(floodgatePrefix(), a == null ? null : a.identityType(),
                config.getAllowedCommandPrefixes(), a == null ? List.of() : a.allowedCommandPrefixes());
    }

    /** Floodgate's prefix (if present), otherwise bedrock-player-prefix from the config; null if neither exists */
    public String floodgatePrefix() {
        FloodgateBridge fg = floodgate;
        String fromFloodgate = fg == null ? null : fg.playerPrefix();
        if (CommandGuard.isValidFloodgatePrefix(fromFloodgate)) return fromFloodgate.trim();
        if (fromFloodgate != null && !fromFloodgate.isEmpty()) {
            log.warn("[CrafterConnect] Floodgate prefix must be a single character (" + CommandGuard.PREFIX_CHARACTERS + "); falling back to config.");
        }
        String cfg = config.getBedrockPlayerPrefix();
        return CommandGuard.isValidFloodgatePrefix(cfg) ? cfg.trim() : null;
    }

    ServerInfo serverInfo() {
        return new ServerInfo(platform.platformId(), BuildInfo.version(), platform.gameVersion(),
                platform.maxPlayers(), platform.serverHost(), platform.serverPort(), floodgatePrefix());
    }

    private void runUpdateCheck() {
        updateResult = new UpdateChecker(BuildInfo.version()).checkSync();
        if (updateResult.hasUpdate()) console("console_update_available", updateResult.getLatestVersion(), updateResult.getDownloadUrl());
    }

    private void checkMinVersionAsync(String websiteId) {
        ScheduledExecutorService exec = io;
        if (exec == null) return;
        exec.execute(() -> {
            String min = new ConnectClient(config.httpBase(), http).fetchMinVersion(websiteId, platform.platformId());
            minVersion = min;
            if (UpdateChecker.isBelowMinimum(BuildInfo.version(), min)) {
                console("console_below_min_version", BuildInfo.version(), min, UpdateChecker.RELEASES_URL);
            }
        });
    }

    private Path dataDir() {
        Path data = platform.dataDirectory().resolve("data");
        try {
            SecureFiles.ensurePrivateDirectory(data);
        } catch (IOException e) {
            log.error("[CrafterConnect] Could not create data folder: " + e.getMessage());
        }
        return data;
    }

    private ConnectionInfo readConnectionInfo() {
        Path file = dataDir().resolve("connection.json");
        if (!Files.exists(file)) return null;
        try {
            return gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), ConnectionInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeConnectionInfo(ConnectionInfo info) throws IOException {
        SecureFiles.writeAtomically(dataDir().resolve("connection.json"), gson.toJson(info));
        connectionInfo = info;
    }

    private void console(String key, Object... args) {
        String text = LanguageManager.stripColors(lang.getMessage(key, args));
        log.warn("[CrafterConnect] " + text);
    }

    private String msg(String key, Object... args) {
        return lang.getMessage("prefix") + lang.getMessage(key, args);
    }

    private static String nonEmpty(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    // --- Platform access ---

    public CrafterConfig config() { return config; }
    public LanguageManager lang() { return lang; }
    public StatisticsManager statsManager() { return statsManager; }
    public PlayerManager playerManager() { return playerManager; }
    public MarketManager marketManager() { return marketManager; }
    public boolean isConnected() {
        CrafterWebSocketClient c = wsClient;
        return c != null && c.isConnected();
    }
    public UpdateCheckResult updateResult() { return updateResult; }
    /** For tests */
    CommandProcessor processor() { return processor; }
    PlaytimeReporter playtime() { return playtime; }
}
