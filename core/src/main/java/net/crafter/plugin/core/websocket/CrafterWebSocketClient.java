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
import org.java_websocket.client.WebSocketClient;
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
        running.set(true);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "crafter-ws-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        log.info("[CrafterConnect] Initializing WebSocket connection...");
        connect();
        
        // Ping görevi
        scheduler.scheduleAtFixedRate(this::sendPingPacket, 30, 30, TimeUnit.SECONDS);
    }

    public void shutdown() {
        running.set(false);
        if (scheduler != null) scheduler.shutdownNow();
        close();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log.info("[CrafterConnect] WebSocket connection established. [OK]");
        reconnectAttempts.set(0);
    }

    @Override
    public void onMessage(String message) {
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
    public void onClose(int code, String reason, boolean remote) {
        if (running.get()) {
            log.warn("[CrafterConnect] Connection closed (Code: {}, Reason: {}). Reconnecting...", code, reason);
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("[CrafterConnect] WebSocket error: {}", ex.getMessage());
    }

    private void handleMessage(CrafterMessage message) {
        String type = message.getType();
        if (type == null) return;

        switch (type.toLowerCase()) {
            case "auth_ok" -> log.info("[CrafterConnect] Authentication successful! [OK]");
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
            send(gson.toJson(new CrafterMessage(MessageType.PING, null)));
        }
    }

    private void sendPong() {
        if (isOpen()) {
            send(gson.toJson(new CrafterMessage(MessageType.PONG, null)));
        }
    }

    private void sendAck(String commandId) {
        if (isOpen()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("commandId", commandId);
            send(gson.toJson(new CrafterMessage(MessageType.ACK, payload)));
        }
    }

    private void scheduleReconnect() {
        if (!running.get()) return;

        int delay = config.getReconnectDelaySeconds();
        int attempts = reconnectAttempts.incrementAndGet();

        log.info("[Crafter] Reconnecting in {} seconds (attempt #{})...", delay, attempts);
        
        scheduler.schedule(() -> {
            if (running.get() && !isOpen()) {
                reconnect();
            }
        }, delay, TimeUnit.SECONDS);
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
            // Backend mesajı yerine dil dosyasındaki mesajı kullan
            executor.sendMessage(username, languageManager.getMessage("prefix") + 
                    languageManager.getMessage("item_used_success", msg)); 
            // Refresh chest
            getChest(username);
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
