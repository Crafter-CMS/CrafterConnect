package net.crafter.plugin.core.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.crafter.plugin.core.BuildInfo;
import net.crafter.plugin.core.LanguageManager;
import net.crafter.plugin.core.MarketManager;
import net.crafter.plugin.core.PlayerManager;
import net.crafter.plugin.core.StatisticsManager;
import net.crafter.plugin.core.config.CrafterConfig;
import net.crafter.plugin.core.connect.ServerInfo;
import net.crafter.plugin.core.delivery.Backoff;
import net.crafter.plugin.core.delivery.CommandProcessor;
import net.crafter.plugin.core.delivery.CommandStore;
import net.crafter.plugin.core.model.*;
import net.crafter.plugin.core.platform.CrafterLog;
import net.crafter.plugin.core.platform.Platform;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Crafter WebSocket bağlantısı (protokol 2, #37 bölüm 3).
 *
 * <ul>
 *   <li>Token ile: {@code wss://api.crafter.net.tr/website/plugin/wss?protocol=2} + {@code Authorization: Bearer cct_…}.
 *   Token adres satırına yazılmaz.</li>
 *   <li>Eski bağlantı: {@code ?website-id=…&plugin-secret=…&server-id=…&protocol=2}. Adres loglanmaz.</li>
 *   <li>Kimlik doğrulamadan sonra {@code hello} (platform, sürüm, Minecraft sürümü, maxPlayers, floodgatePrefix),
 *   30 sn'de bir {@code heartbeat} gönderilir ve bekleyen ACK/NACK'ler yollanır.</li>
 *   <li>Bağlantı koparsa üstel bekleme ve rastgele sapmayla yeniden bağlanır. Token geçersizse ya da başka bir
 *   kodla yeniden bağlanıldıysa denemeyi bırakır.</li>
 * </ul>
 */
public class CrafterWebSocketClient extends WebSocketClient {

    static final int HEARTBEAT_SECONDS = 30;
    /**
     * Bağlantı canlılığı: istemci 30 sn'de bir ping gönderir (Cloudflare'in 100 sn boşta kalma sınırının altında;
     * backend de 30 sn'de bir ping atar). 90 sn hiçbir veri ya da pong gelmezse soket yarı açık sayılır ve yeniden
     * kurulur. Java-WebSocket'in kendi kontrolü (connectionLostTimeout × 1,5) de 90 sn'ye ayarlanır; 1.x'teki
     * 25 sn (37,5 sn tolerans) ve 60 sn watchdog geç gelen bir pong'da bağlantıyı gereksiz yere koparıyordu.
     */
    static final int PING_SECONDS = 30;
    static final int DEAD_AFTER_SECONDS = 90;
    static final int MAX_BACKOFF_SECONDS = 120;

    /** Backend'in v2 kimlik doğrulama bilgisi */
    public record AuthInfo(String websiteId, String serverId, String identityType, List<String> allowedCommandPrefixes) {}

    /** Bağlantı olayları (CrafterConnectService dinler) */
    public interface Listener {
        void onAuthenticated(AuthInfo info);
        /** Token geçersiz ya da kaldırıldı: yeniden bağlanma durdu */
        void onTokenRejected(String reason);
        default void onDisconnected() {}
        /** player_stats_ack: ACK'lenen örnek anahtarları (#529) */
        default void onPlayerStatsAck(List<String> keys) {}
    }

    private final CrafterConfig config;
    private final Platform platform;
    private final CommandProcessor processor;
    private final StatisticsManager statsManager;
    private final PlayerManager playerManager;
    private final MarketManager marketManager;
    private final Supplier<LanguageManager> languageManager;
    private final Supplier<ServerInfo> serverInfo;
    private final Listener listener;
    private final CrafterLog log;
    private final Gson gson = new Gson();
    private final Backoff backoff;

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private volatile boolean stopReconnecting = false;
    private volatile String lastError;
    private volatile long lastReceivedTimestamp = System.currentTimeMillis();
    private ScheduledFuture<?> reconnectFuture = null;

    public CrafterWebSocketClient(CrafterConfig config, Platform platform, CommandProcessor processor,
                                  StatisticsManager statsManager, PlayerManager playerManager, MarketManager marketManager,
                                  Supplier<LanguageManager> languageManager, Supplier<ServerInfo> serverInfo,
                                  Listener listener) {
        super(createURI(config), createHeaders(config));
        this.config = config;
        this.platform = platform;
        this.processor = processor;
        this.statsManager = statsManager;
        this.playerManager = playerManager;
        this.marketManager = marketManager;
        this.languageManager = languageManager;
        this.serverInfo = serverInfo;
        this.listener = listener;
        this.log = platform.log();
        this.backoff = new Backoff(config.getReconnectDelaySeconds(), MAX_BACKOFF_SECONDS);

        // RFC 6455 Ping/Pong; kütüphanenin kendi kontrolü 60 × 1,5 = 90 sn pong gelmezse kapatır
        setConnectionLostTimeout(DEAD_AFTER_SECONDS * 2 / 3);
        setTcpNoDelay(true);
    }

    static URI createURI(CrafterConfig config) {
        String base = config.wsBase() + "/website/plugin/wss?protocol=" + BuildInfo.PROTOCOL_VERSION;
        if (config.mode() == CrafterConfig.Mode.LEGACY) {
            base += "&website-id=" + enc(config.getWebsiteId())
                    + "&plugin-secret=" + enc(config.getPluginSecret())
                    + "&server-id=" + enc(config.getServerId());
        }
        return URI.create(base);
    }

    static Map<String, String> createHeaders(CrafterConfig config) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "CrafterConnect/" + BuildInfo.version());
        if (config.mode() == CrafterConfig.Mode.TOKEN) {
            headers.put("Authorization", "Bearer " + config.getServerToken());
        }
        return headers;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v.trim(), StandardCharsets.UTF_8);
    }

    public void start() {
        if (!config.isConfigured()) return;

        running.set(true);
        processor.setSender(this::sendOutgoing);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crafter-ws-scheduler");
            t.setDaemon(true);
            return t;
        });

        log.info("[CrafterConnect] Connecting to " + config.wsBase() + " ("
                + (config.mode() == CrafterConfig.Mode.TOKEN ? "server token" : "legacy connection") + ")...");
        try {
            connect();
        } catch (Exception e) {
            log.error("[CrafterConnect] Failed to initiate connection: " + e.getMessage());
            scheduleReconnect();
        }

        scheduler.scheduleWithFixedDelay(this::runWatchdog, PING_SECONDS, PING_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    public void shutdown() {
        running.set(false);
        isReconnecting.set(false);
        processor.setSender(null);
        cancelReconnectFuture();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        try {
            close();
        } catch (Exception ignored) {
            // zaten kapalı
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("[CrafterConnect] WebSocket connection established.");
        reconnectAttempts.set(0);
        isReconnecting.set(false);
        lastReceivedTimestamp = System.currentTimeMillis();
        cancelReconnectFuture();
    }

    @Override
    public void onMessage(String message) {
        lastReceivedTimestamp = System.currentTimeMillis();
        try {
            CrafterMessage msg = gson.fromJson(message, CrafterMessage.class);
            if (msg != null) handleMessage(msg);
        } catch (Exception e) {
            log.error("[CrafterConnect] Message processing error: " + e.getMessage());
        }
    }

    @Override
    public void onWebsocketPing(WebSocket conn, Framedata f) {
        super.onWebsocketPing(conn, f);
        lastReceivedTimestamp = System.currentTimeMillis();
    }

    @Override
    public void onWebsocketPong(WebSocket conn, Framedata f) {
        super.onWebsocketPong(conn, f);
        lastReceivedTimestamp = System.currentTimeMillis();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        authenticated.set(false);
        if (listener != null) listener.onDisconnected();
        if (!running.get()) return;
        if (isTokenRevocation(code, reason)) {
            rejectToken(reason);
            return;
        }
        log.warn("[CrafterConnect] Connection closed (code " + code + (reason == null || reason.isBlank() ? "" : ", " + reason) + "). Reconnecting...");
        scheduleReconnect();
    }

    /** 1008 "Invalid server token" ya da 4001 (yeniden bağlama/silme) yalnızca token bağlantısında kalıcıdır */
    boolean isTokenRevocation(int code, String reason) {
        if (config.mode() != CrafterConfig.Mode.TOKEN) return false;
        String r = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        return code == 4001 || (code == 1008 && r.contains("token"));
    }

    private void rejectToken(String reason) {
        stopReconnecting = true;
        lastError = "server_token_invalid";
        log.error("[CrafterConnect] The server token is no longer valid" + (reason == null || reason.isBlank() ? "" : " (" + reason + ")")
                + ". Create a new code in the dashboard and run /crafter connect <code>.");
        if (listener != null) listener.onTokenRejected(reason);
    }

    @Override
    public void onError(Exception ex) {
        log.error("[CrafterConnect] WebSocket error: " + ex.getMessage());
        if (running.get() && !isOpen()) scheduleReconnect();
    }

    void handleMessage(CrafterMessage message) {
        String type = message.getType();
        if (type == null) return;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "auth_ok", "auth_success" -> handleAuthSuccess(message);
            case "auth_fail" -> log.error("[CrafterConnect] Authentication failed. Check the connection settings.");
            case "error" -> handleError(message);
            case "command" -> handleCommand(message);
            case "statistics" -> handleStatistics(message);
            case "player_info" -> handlePlayerInfo(message);
            case "products_list" -> handleProductsList(message);
            case "purchase_response" -> handlePurchaseResponse(message);
            case "chest_content" -> handleChestContent(message);
            case "use_item_response" -> handleUseItemResponse(message);
            case "player_stats_ack" -> handlePlayerStatsAck(message);
            case "ping" -> sendRaw(gson.toJson(new CrafterMessage(MessageType.PONG, null)));
            default -> log.debug("[CrafterConnect] Unknown message type: " + type);
        }
    }

    private void handleAuthSuccess(CrafterMessage message) {
        authenticated.set(true);
        lastError = null;
        JsonObject p = message.getPayload();
        List<String> prefixes = new ArrayList<>();
        if (p != null && p.has("settings") && p.get("settings").isJsonObject()) {
            JsonElement list = p.getAsJsonObject("settings").get("allowedCommandPrefixes");
            if (list != null && list.isJsonArray()) {
                for (JsonElement e : list.getAsJsonArray()) {
                    if (e.isJsonPrimitive()) prefixes.add(e.getAsString());
                }
            }
        }
        AuthInfo info = new AuthInfo(str(p, "websiteId"), str(p, "serverId"), str(p, "identityType"), prefixes);
        log.info("[CrafterConnect] Authenticated" + (info.serverId() != null ? " (server " + info.serverId() + ")" : "") + ".");
        if (listener != null) listener.onAuthenticated(info);

        sendHello();
        if (config.isReportPlayers()) syncOnlinePlayers();
        processor.flush();
    }

    private void handleError(CrafterMessage message) {
        String code = message.getCode();
        lastError = code;
        if ("server_token_invalid".equals(code)) {
            rejectToken(null);
            return;
        }
        log.error("[CrafterConnect] Server reported an error: " + (code == null ? "unknown" : code)
                + (message.getMessage() == null ? "" : " (" + message.getMessage() + ")"));
    }

    private void handleCommand(CrafterMessage message) {
        PendingCommand pc = null;
        if (message.getPayload() != null) {
            pc = gson.fromJson(message.getPayload(), PendingCommand.class);
        }
        if ((pc == null || pc.getCommand() == null) && message.getCommand() != null) {
            pc = new PendingCommand(null, message.getCommand(), null);
        }
        if (pc == null || pc.getCommand() == null) return;
        pc.setQueuedAt(0);
        processor.onCommand(pc);
    }

    // --- Gönderim ---

    private boolean sendOutgoing(CommandStore.Outgoing o) {
        if (!isOpen() || !authenticated.get()) return false;
        JsonObject data = new JsonObject();
        data.addProperty("commandId", o.commandId);
        if (o.isAck()) data.addProperty("status", o.status);
        else data.addProperty("reason", o.reason);
        return sendEvent(o.kind, data);
    }

    void sendHello() {
        ServerInfo info = serverInfo.get();
        JsonObject data = new JsonObject();
        data.addProperty("platform", info.platform());
        data.addProperty("pluginVersion", info.pluginVersion());
        data.addProperty("protocol", BuildInfo.PROTOCOL_VERSION);
        data.addProperty("transport", "ws");
        if (info.gameVersion() != null) data.addProperty("gameVersion", info.gameVersion());
        if (info.maxPlayers() > 0) data.addProperty("maxPlayers", info.maxPlayers());
        if (info.floodgatePrefix() != null && !info.floodgatePrefix().isEmpty()) {
            data.addProperty("floodgatePrefix", info.floodgatePrefix());
        }
        // Yetenekler: player_stats gönderen sunucuda backend eski join/leave süre hesabını kapatır (#529)
        JsonArray capabilities = new JsonArray();
        if (platform.supportsPlayerStats() && config.isPlaytimeEnabled()) capabilities.add("player_stats");
        data.add("capabilities", capabilities);
        sendEvent("hello", data);
    }

    private void sendHeartbeat() {
        if (!running.get() || !isOpen() || !authenticated.get()) return;
        try {
            JsonObject data = new JsonObject();
            data.addProperty("online", platform.onlinePlayers());
            data.addProperty("maxPlayers", platform.maxPlayers());
            sendEvent("heartbeat", data);
        } catch (Throwable t) {
            log.debug("[CrafterConnect] Heartbeat failed: " + t.getMessage());
        }
    }

    /** Oynama süresi örnekleri (#529); bağlı ve doğrulanmış değilse false */
    public boolean sendPlayerStats(JsonObject data) {
        if (!isOpen() || !authenticated.get()) return false;
        return sendEvent("player_stats", data);
    }

    private void handlePlayerStatsAck(CrafterMessage message) {
        JsonObject p = message.getPayload();
        List<String> keys = new ArrayList<>();
        if (p != null && p.has("keys") && p.get("keys").isJsonArray()) {
            for (JsonElement e : p.getAsJsonArray("keys")) {
                if (e.isJsonPrimitive()) keys.add(e.getAsString());
            }
        }
        if (listener != null) listener.onPlayerStatsAck(keys);
    }

    private boolean sendEvent(String eventName, JsonObject data) {
        JsonObject json = new JsonObject();
        json.addProperty("event", eventName);
        json.add("data", data);
        return sendRaw(gson.toJson(json));
    }

    private boolean sendRaw(String text) {
        if (!isOpen()) return false;
        try {
            send(text);
            return true;
        } catch (Exception e) {
            log.debug("[CrafterConnect] Send failed: " + e.getMessage());
            return false;
        }
    }

    // --- Yeniden bağlanma ---

    private void runWatchdog() {
        if (!running.get()) return;
        try {
            if (isOpen()) {
                try {
                    sendPing();
                } catch (Exception e) {
                    log.debug("[CrafterConnect] Failed to send protocol ping: " + e.getMessage());
                }
                long elapsed = System.currentTimeMillis() - lastReceivedTimestamp;
                // DEAD_AFTER_SECONDS boyunca hiçbir veri ya da pong gelmediyse soket yarı açık kabul edilir
                if (elapsed > DEAD_AFTER_SECONDS * 1000L) {
                    log.warn("[CrafterConnect] No response from server for " + elapsed / 1000 + "s. Resetting the connection...");
                    try {
                        closeConnection(1006, "Watchdog timeout");
                    } catch (Exception e) {
                        try { close(); } catch (Exception ignored) { /* kapalı */ }
                    }
                    scheduleReconnect();
                }
            } else if (!isReconnecting.get()) {
                scheduleReconnect();
            }
        } catch (Throwable t) {
            log.error("[CrafterConnect] Watchdog error: " + t.getMessage());
        }
    }

    private synchronized void scheduleReconnect() {
        if (!running.get() || stopReconnecting) return;
        if (isOpen()) {
            isReconnecting.set(false);
            return;
        }
        if (isReconnecting.get()) return;
        isReconnecting.set(true);

        int maxAttempts = config.getMaxReconnectAttempts();
        int attempts = reconnectAttempts.incrementAndGet();
        if (maxAttempts > 0 && attempts > maxAttempts) {
            log.error("[CrafterConnect] Reconnect stopped: maximum attempts (" + maxAttempts + ") reached.");
            isReconnecting.set(false);
            return;
        }
        int delay = backoff.delaySeconds(attempts);
        log.info("[CrafterConnect] Reconnecting in " + delay + " seconds (attempt #" + attempts + ")...");

        cancelReconnectFuture();
        if (scheduler == null || scheduler.isShutdown()) {
            isReconnecting.set(false);
            return;
        }
        reconnectFuture = scheduler.schedule(() -> {
            boolean open = false;
            try {
                // Bloklayan sürüm: el sıkışma bitmeden "bağlanamadı" sanılıp yeni deneme planlanmaz
                if (running.get() && !stopReconnecting && !isOpen()) open = reconnectBlocking();
                else open = isOpen();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                log.error("[CrafterConnect] Reconnect attempt failed: " + t.getMessage());
            } finally {
                isReconnecting.set(false);
                if (!open && !isOpen()) scheduleReconnect();
            }
        }, delay, TimeUnit.SECONDS);
    }

    private synchronized void cancelReconnectFuture() {
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
        }
        reconnectFuture = null;
    }

    // --- Oyuncu olayları ---

    private void syncOnlinePlayers() {
        try {
            for (String username : playerManager.getOnlineUsernames()) {
                sendPlayerEvent("player_join", username, null);
            }
        } catch (Exception e) {
            log.error("[CrafterConnect] Error synchronizing online players: " + e.getMessage());
        }
    }

    /** Oyuncu girdi: backend'e bildirir, sandığı ister; kuyruk çekirdek serviste işlenir */
    public void onPlayerJoin(String username, PlayerIdentity identity) {
        if (config.isReportPlayers()) sendPlayerEvent("player_join", username, identity);
        getChest(username, identity);
    }

    public void onPlayerQuit(String username, PlayerIdentity identity) {
        if (config.isReportPlayers()) sendPlayerEvent("player_left", username, identity);
    }

    private void sendPlayerEvent(String event, String username, PlayerIdentity identity) {
        sendPlayerEvent(event, username, identity, false);
    }

    /** refresh: yalnızca bakiye/rol tazeleme; backend oturum başlatmaz (#529) */
    private void sendPlayerEvent(String event, String username, PlayerIdentity identity, boolean refresh) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        addIdentity(data, username, identity);
        if (refresh) data.addProperty("refresh", true);
        sendEvent(event, data);
    }

    public void getProducts() {
        sendEvent("get_products", new JsonObject());
    }

    public void purchaseProduct(String username, PlayerIdentity identity, String productId) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        addIdentity(data, username, identity);
        data.addProperty("productId", productId);
        sendEvent("purchase_product", data);
    }

    public void getChest(String username, PlayerIdentity identity) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        addIdentity(data, username, identity);
        sendEvent("get_chest", data);
    }

    public void useChestItem(String username, PlayerIdentity identity, String chestItemId) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        addIdentity(data, username, identity);
        data.addProperty("chestItemId", chestItemId);
        sendEvent("use_chest_item", data);
    }

    private void addIdentity(JsonObject data, String username, PlayerIdentity identity) {
        PlayerIdentity id = identity != null ? identity : new PlayerIdentity(PlayerIdentity.MINECRAFT_JAVA, username);
        JsonObject obj = new JsonObject();
        obj.addProperty("type", id.getType());
        obj.addProperty("value", id.getValue());
        data.add("identity", obj);
    }

    // --- Mağaza mesajları ---

    private void handlePlayerInfo(CrafterMessage message) {
        if (message.getPayload() == null) return;
        PlayerData data = gson.fromJson(message.getPayload(), PlayerData.class);
        playerManager.updatePlayer(data);
    }

    private void handleStatistics(CrafterMessage message) {
        if (message.getData() == null) return;
        StatisticsData stats = gson.fromJson(message.getData(), StatisticsData.class);
        statsManager.updateStats(stats);
    }

    private void handleProductsList(CrafterMessage message) {
        JsonObject payload = message.getPayload();
        if (payload == null) return;
        List<Product> products = gson.fromJson(payload.get("products"), new TypeToken<List<Product>>() {}.getType());
        List<Category> categories = gson.fromJson(payload.get("categories"), new TypeToken<List<Category>>() {}.getType());
        if (products == null) products = new ArrayList<>();
        if (categories == null) categories = new ArrayList<>();
        marketManager.updateMarket(products, categories);
        log.debug("[CrafterConnect] Market updated: " + products.size() + " products, " + categories.size() + " categories.");
    }

    private void handlePurchaseResponse(CrafterMessage message) {
        JsonObject payload = message.getPayload();
        String username = str(payload, "username");
        if (username == null) return;
        boolean success = payload.has("success") && payload.get("success").getAsBoolean();
        String msg = Objects.requireNonNullElse(str(payload, "message"), "");
        if (success) {
            // Satın alma sonrası sandık ve bakiye tazelenir
            getChest(username, null);
            sendPlayerEvent("player_join", username, null, true);
        }
        platform.handlePurchaseResponse(username, success, msg);
    }

    private void handleChestContent(CrafterMessage message) {
        JsonObject payload = message.getPayload();
        String username = str(payload, "username");
        if (username == null) return;
        List<ChestItem> items = gson.fromJson(payload.get("items"), new TypeToken<List<ChestItem>>() {}.getType());
        marketManager.updatePlayerChest(username, items == null ? new ArrayList<>() : items);
    }

    private void handleUseItemResponse(CrafterMessage message) {
        JsonObject payload = message.getPayload();
        String username = str(payload, "username");
        if (username == null) return;
        boolean success = payload.has("success") && payload.get("success").getAsBoolean();
        String msg = Objects.requireNonNullElse(str(payload, "message"), "");
        LanguageManager lang = languageManager.get();
        if (success) {
            getChest(username, null);
            sendPlayerEvent("player_join", username, null, true);
            platform.sendMessage(username, lang.getMessage("prefix") + lang.getMessage("item_used_success", msg));
        } else {
            platform.sendMessage(username, lang.getMessage("prefix") + lang.getMessage("item_used_error", msg));
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) return null;
        return obj.get(key).getAsString();
    }

    public boolean isConnected() {
        return isOpen() && authenticated.get();
    }

    /** Son hata kodu (server_token_invalid, LICENSE_ERROR …), yoksa null */
    public String lastError() {
        return lastError;
    }

    public boolean isStopped() {
        return stopReconnecting;
    }
}
