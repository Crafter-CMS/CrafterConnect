package net.crafter.plugin.core.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.crafter.plugin.core.CommandExecutor;
import net.crafter.plugin.core.config.CrafterConfig;
import net.crafter.plugin.core.model.CrafterMessage;
import net.crafter.plugin.core.model.MessageType;
import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.StatisticsManager;
import net.crafter.plugin.core.MarketManager;
import net.crafter.plugin.core.model.*;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java-WebSocket kütüphanesi kullanarak crafter.net.tr bağlantısını yöneten istemci.
 */
public class CrafterWebSocketClient extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(CrafterWebSocketClient.class);

    private final CrafterConfig config;
    private final CommandExecutor executor;
    private final StatisticsManager statsManager;
    private final net.crafter.plugin.core.PlayerManager playerManager;
    private final net.crafter.plugin.core.MarketManager marketManager;
    private final net.crafter.plugin.core.LanguageManager languageManager;
    private final Gson gson;

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private volatile long lastReceivedTimestamp = System.currentTimeMillis();
    private ScheduledFuture<?> reconnectFuture = null;

    // Oyuncu offline ise bekletilen komutlar (player → komut listesi)
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<PendingCommand>> offlineQueue
            = new ConcurrentHashMap<>();

    public CrafterWebSocketClient(CrafterConfig config, CommandExecutor executor, StatisticsManager statsManager, net.crafter.plugin.core.PlayerManager playerManager, net.crafter.plugin.core.MarketManager marketManager, net.crafter.plugin.core.LanguageManager languageManager) {
        super(createURI(config));
        this.config = config;
        this.executor = executor;
        this.statsManager = statsManager;
        this.playerManager = playerManager;
        this.marketManager = marketManager;
        this.languageManager = languageManager;
        this.gson = new GsonBuilder().create();

        // Otomatik bağlantı kaybı kontrolü (25 saniye)
        // Belirli aralıklarla RFC 6455 Ping gönderir, Pong gelmezse soketi kapatıp onClose tetikler.
        setConnectionLostTimeout(25);
        setTcpNoDelay(true);
    }

    private static URI createURI(CrafterConfig config) {
        String protocol = config.isUseSsl() ? "wss" : "ws";
        String url = String.format("%s://%s/website/plugin/wss?website-id=%s&plugin-secret=%s&server-id=%s",
                protocol,
                config.getApiUrl(),
                config.getWebsiteId(),
                config.getPluginSecret(),
                config.getServerId());
        return URI.create(url);
    }

    public void start() {
        if (!config.isConfigured()) {
            log.warn("================================================================");
            log.warn("[CrafterConnect] Eklenti henüz yapılandırılmamış (varsayılan config)!");
            log.warn("[CrafterConnect] Lütfen config.yml dosyasındaki 'website-id', 'plugin-secret'");
            log.warn("[CrafterConnect] ve 'server-id' alanlarını dashboard bilgilerinizle doldurun.");
            log.warn("[CrafterConnect] WebSocket bağlantı denemeleri yapılmayacaktır.");
            log.warn("================================================================");
            return;
        }

        running.set(true);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crafter-ws-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        log.info("[CrafterConnect] Initializing WebSocket connection...");
        try {
            connect();
        } catch (Exception e) {
            log.error("[CrafterConnect] Failed to initiate connection: {}", e.getMessage());
            scheduleReconnect();
        }
        
        // Watchdog & Heartbeat görevi (her 15 saniyede bir soket durumunu kontrol eder)
        scheduler.scheduleWithFixedDelay(this::runWatchdog, 15, 15, TimeUnit.SECONDS);
    }

    public void shutdown() {
        running.set(false);
        isReconnecting.set(false);
        cancelReconnectFuture();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        try {
            close();
        } catch (Exception ignored) {}
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("[CrafterConnect] WebSocket connection established. [OK]");
        reconnectAttempts.set(0);
        isReconnecting.set(false);
        lastReceivedTimestamp = System.currentTimeMillis();
        cancelReconnectFuture();

        // Yeniden bağlantıda online oyuncuları backend'e senkronize et
        syncOnlinePlayers();
    }

    @Override
    public void onMessage(String message) {
        lastReceivedTimestamp = System.currentTimeMillis();
        if (config.isDebug()) {
            log.info("[CrafterConnect] Incoming Message: {}", message);
        }

        try {
            CrafterMessage msg = gson.fromJson(message, CrafterMessage.class);
            handleMessage(msg);
        } catch (Exception e) {
            log.error("[CrafterConnect] Message processing error: {}", e.getMessage());
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
        if (running.get() && config.isConfigured()) {
            log.warn("[CrafterConnect] Connection closed (Code: {}, Reason: {}, Remote: {}). Reconnecting...", code, reason, remote);
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("[CrafterConnect] WebSocket error: {}", ex.getMessage());
        if (running.get() && config.isConfigured() && !isOpen()) {
            scheduleReconnect();
        }
    }

    private void handleMessage(CrafterMessage message) {
        String type = message.getType();
        if (type == null) return;

        switch (type.toLowerCase()) {
            case "auth_ok", "auth_success" -> log.info("[CrafterConnect] Authentication successful! [OK]");
            case "auth_fail" -> log.error("[CrafterConnect] Authentication failed! Please check your config.");
            case "command" -> handleCommand(message);
            case "statistics" -> handleStatistics(message);
            case "player_info" -> handlePlayerInfo(message);
            case "products_list" -> handleProductsList(message);
            case "purchase_response" -> handlePurchaseResponse(message);
            case "chest_content" -> handleChestContent(message);
            case "use_item_response" -> handleUseItemResponse(message);
            case "ping" -> sendPong();
        }
    }

    private void handlePlayerInfo(CrafterMessage message) {
        if (message.getPayload() == null) return;
        try {
            net.crafter.plugin.core.model.PlayerData data = gson.fromJson(message.getPayload(), net.crafter.plugin.core.model.PlayerData.class);
            if (playerManager != null) playerManager.updatePlayer(data);
        } catch (Exception e) {
            log.error("[CrafterConnect] Player info parse error: {}", e.getMessage());
        }
    }

    private void handleStatistics(CrafterMessage message) {
        if (message.getData() == null) return;
        try {
            StatisticsData stats = gson.fromJson(message.getData(), StatisticsData.class);
            if (statsManager != null) statsManager.updateStats(stats);
        } catch (Exception e) {
            log.error("[CrafterConnect] Statistics parse error: {}", e.getMessage());
        }
    }

    private void handleCommand(CrafterMessage message) {
        PendingCommand pc = null;
        
        // Try parsing from payload first
        if (message.getPayload() != null) {
            pc = gson.fromJson(message.getPayload(), PendingCommand.class);
        }
        
        // If no payload or payload didn't have command, try direct command field
        if ((pc == null || pc.getCommand() == null) && message.getCommand() != null) {
            pc = new PendingCommand();
            pc.setCommand(message.getCommand());
        }

        if (pc == null || pc.getCommand() == null) return;

        // Offline queue logic
        String targetPlayer = pc.getPlayer();
        if (targetPlayer != null && !targetPlayer.isBlank()) {
            if (!executor.isPlayerOnline(targetPlayer)) {
                log.info("[CrafterConnect] Player {} is offline. Adding command to queue.", targetPlayer);
                addToQueue(targetPlayer, pc);
                return; // Don't execute now
            }
        }

        log.info("[CrafterConnect] Executing command: {}", pc.getCommand());
        boolean success = executor.execute(pc);
        if (success && pc.getCommandId() != null) {
            sendAck(pc.getCommandId());
        }
    }

    private void sendPingPacket() {
        if (isOpen()) {
            try {
                send(gson.toJson(new CrafterMessage(MessageType.PING, null)));
            } catch (Exception ignored) {}
        }
    }

    private void sendPong() {
        if (isOpen()) {
            try {
                send(gson.toJson(new CrafterMessage(MessageType.PONG, null)));
            } catch (Exception ignored) {}
        }
    }

    private void sendAck(String commandId) {
        if (isOpen()) {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("commandId", commandId);
                send(gson.toJson(new CrafterMessage(MessageType.ACK, payload)));
            } catch (Exception e) {
                log.error("[CrafterConnect] Failed to send ack for command {}: {}", commandId, e.getMessage());
            }
        }
    }

    private void runWatchdog() {
        if (!running.get() || !config.isConfigured()) return;

        try {
            if (isOpen()) {
                long now = System.currentTimeMillis();
                long elapsed = now - lastReceivedTimestamp;

                // Protokol seviyesinde RFC 6455 Ping gönder (Keep-Alive)
                try {
                    sendPing();
                } catch (Exception e) {
                    if (config.isDebug()) {
                        log.debug("[CrafterConnect] Failed to send protocol ping: {}", e.getMessage());
                    }
                }

                // Eğer 60 saniyeden uzun süredir backend'den hiçbir veri veya ping/pong gelmemişse:
                // Soket askıda/donmuş (half-open zombie) kabul edilir.
                if (elapsed > 60_000) {
                    log.warn("[CrafterConnect] Watchdog: No response from server for {}s (half-open connection). Resetting socket...", elapsed / 1000);
                    try {
                        closeConnection(1006, "Watchdog timeout");
                    } catch (Exception e) {
                        try { close(); } catch (Exception ignored) {}
                    }
                    scheduleReconnect();
                }
            } else if (!isReconnecting.get()) {
                log.warn("[CrafterConnect] Watchdog: Socket is closed and no reconnect is scheduled. Triggering reconnect...");
                scheduleReconnect();
            }
        } catch (Throwable t) {
            log.error("[CrafterConnect] Watchdog error: {}", t.getMessage());
        }
    }

    private synchronized void scheduleReconnect() {
        if (!running.get() || !config.isConfigured()) return;

        if (isOpen()) {
            isReconnecting.set(false);
            return;
        }

        if (isReconnecting.get()) {
            return;
        }

        isReconnecting.set(true);

        int maxAttempts = config.getMaxReconnectAttempts();
        int attempts = reconnectAttempts.incrementAndGet();

        if (maxAttempts > 0 && attempts > maxAttempts) {
            log.error("[CrafterConnect] Reconnect aborted: maximum attempts ({}) reached.", maxAttempts);
            isReconnecting.set(false);
            return;
        }

        int baseDelay = Math.max(1, config.getReconnectDelaySeconds());
        // Exponential backoff capped at 60 seconds
        int delay = Math.min(baseDelay * Math.min(attempts, 12), 60);

        log.info("[CrafterConnect] Reconnecting in {} seconds (attempt #{})...", delay, attempts);

        cancelReconnectFuture();
        if (scheduler == null || scheduler.isShutdown()) {
            isReconnecting.set(false);
            return;
        }

        reconnectFuture = scheduler.schedule(() -> {
            try {
                if (running.get() && config.isConfigured() && !isOpen()) {
                    log.info("[CrafterConnect] Attempting to reconnect (attempt #{})...", attempts);
                    reconnect();
                }
            } catch (Throwable t) {
                log.error("[CrafterConnect] Reconnect attempt failed unexpectedly: {}", t.getMessage());
            } finally {
                if (!isOpen()) {
                    isReconnecting.set(false);
                    scheduleReconnect();
                } else {
                    isReconnecting.set(false);
                }
            }
        }, delay, TimeUnit.SECONDS);
    }

    private synchronized void cancelReconnectFuture() {
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    private void syncOnlinePlayers() {
        if (playerManager == null) return;
        try {
            for (String username : playerManager.getOnlineUsernames()) {
                sendPlayerJoin(username);
            }
        } catch (Exception e) {
            log.error("[CrafterConnect] Error synchronizing online players on reconnect: {}", e.getMessage());
        }
    }

    public void onPlayerJoin(String username) {
        // Send join notification to backend
        sendPlayerJoin(username);

        // Fetch chest content for the player initially
        getChest(username);

        ConcurrentLinkedQueue<PendingCommand> queue = offlineQueue.remove(username);
        if (queue != null) {
            while (!queue.isEmpty()) {
                PendingCommand pc = queue.poll();
                if (pc != null) {
                    boolean success = executor.execute(pc);
                    if (success && pc.getCommandId() != null) {
                        sendAck(pc.getCommandId());
                    }
                }
            }
        }
    }

    private void sendPlayerJoin(String username) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        sendEvent("player_join", data);
    }

    public void onPlayerQuit(String username) {
        sendPlayerLeft(username);
    }

    private void sendPlayerLeft(String username) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        sendEvent("player_left", data);
    }

    public void getProducts() {
        sendEvent("get_products", new JsonObject());
    }

    public void purchaseProduct(String username, String productId) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("productId", productId);
        sendEvent("purchase_product", data);
    }

    public void getChest(String username) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        sendEvent("get_chest", data);
    }

    public void useChestItem(String username, String chestItemId) {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("chestItemId", chestItemId);
        sendEvent("use_chest_item", data);
    }

    private void sendEvent(String eventName, JsonObject data) {
        if (!isOpen()) return;
        try {
            JsonObject json = new JsonObject();
            json.addProperty("event", eventName);
            json.add("data", data);
            send(gson.toJson(json));
        } catch (Exception e) {
            log.error("[CrafterConnect] Failed to send event {}: {}", eventName, e.getMessage());
        }
    }

    // --- Message Handlers for Marketplace ---

    private void handleProductsList(CrafterMessage message) {
        JsonObject payload = message.getPayload().getAsJsonObject();
        List<Product> products = gson.fromJson(payload.get("products"), new com.google.gson.reflect.TypeToken<List<Product>>(){}.getType());
        List<Category> categories = gson.fromJson(payload.get("categories"), new com.google.gson.reflect.TypeToken<List<Category>>(){}.getType());
        
        if (marketManager != null) {
            marketManager.updateMarket(products, categories);
            log.info("[CrafterConnect] Market updated with {} products and {} categories.", products.size(), categories.size());
        }
    }

    private void handlePurchaseResponse(CrafterMessage message) {
        JsonObject payload = message.getPayload().getAsJsonObject();
        String username = payload.get("username").getAsString();
        boolean success = payload.get("success").getAsBoolean();
        String msg = payload.get("message").getAsString();
        
        if (success) {
            // Satın alım sonrası hem sandığı hem de oyuncu bilgilerini (bakiye) anında tazele
            getChest(username);
            sendPlayerJoin(username);
        }

        executor.handlePurchaseResponse(username, success, msg);
    }

    private void handleChestContent(CrafterMessage message) {
        JsonObject payload = message.getPayload().getAsJsonObject();
        String username = payload.get("username").getAsString();
        List<ChestItem> items = gson.fromJson(payload.get("items"), new com.google.gson.reflect.TypeToken<List<ChestItem>>(){}.getType());
        
        if (marketManager != null) {
            marketManager.updatePlayerChest(username, items);
            log.info("[CrafterConnect] Chest updated for {}: {} items", username, items.size());
        }
    }

    private void handleUseItemResponse(CrafterMessage message) {
        JsonObject payload = message.getPayload().getAsJsonObject();
        String username = payload.get("username").getAsString();
        boolean success = payload.get("success").getAsBoolean();
        String msg = payload.get("message").getAsString();
        
        if (success) {
            // Eşya kullanımı sonrası hem sandığı hem de oyuncu bilgilerini (bakiye/rol) anında tazele
            getChest(username);
            sendPlayerJoin(username);

            // Backend mesajı yerine dil dosyasındaki mesajı kullan
            executor.sendMessage(username, languageManager.getMessage("prefix") + 
                    languageManager.getMessage("item_used_success", msg)); 
        } else {
            executor.sendMessage(username, languageManager.getMessage("prefix") + 
                    languageManager.getMessage("item_used_error", msg));
        }
    }

    private void addToQueue(String player, PendingCommand pc) {
        offlineQueue.computeIfAbsent(player.toLowerCase(), k -> new ConcurrentLinkedQueue<>()).add(pc);
    }

    public int getOfflineQueueSize() {
        return offlineQueue.values().stream().mapToInt(ConcurrentLinkedQueue::size).sum();
    }

    public boolean isConnected() {
        return isOpen();
    }
}
