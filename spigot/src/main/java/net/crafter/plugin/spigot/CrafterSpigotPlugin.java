package net.crafter.plugin.spigot;

import net.crafter.plugin.core.StatisticsManager;
import net.crafter.plugin.core.config.CrafterConfig;
import net.crafter.plugin.core.websocket.CrafterWebSocketClient;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;

/**
 * Spigot ana plugin sınıfı.
 */
public class CrafterSpigotPlugin extends JavaPlugin implements Listener {

    private CrafterWebSocketClient wsClient;
    private CrafterConfig crafterConfig;
    private StatisticsManager statsManager;
    private net.crafter.plugin.core.PlayerManager playerManager;
    private net.crafter.plugin.core.MarketManager marketManager;
    private net.crafter.plugin.spigot.menu.MenuManager menuManager;
    private net.crafter.plugin.core.LanguageManager languageManager;
    private final java.util.Set<String> pendingShopPlayers = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private volatile boolean isExpired = false;
    private volatile net.crafter.plugin.core.UpdateCheckResult updateResult = null;

    @Override
    public void onEnable() {
        getLogger().info("=== CrafterConnect (Spigot) is starting ===");

        // config.yml create/load
        saveDefaultConfig();
        crafterConfig = loadCrafterConfig();

        // Initialize managers
        statsManager = new StatisticsManager();
        playerManager = new net.crafter.plugin.core.PlayerManager();
        marketManager = new net.crafter.plugin.core.MarketManager();
        marketManager.setOnUpdate(this::onMarketUpdate);
        marketManager.setOnChestUpdate((username, items) -> {
            getServer().getScheduler().runTask(this, () -> {
                Player p = getServer().getPlayerExact(username);
                if (p != null && p.getOpenInventory() != null && p.getOpenInventory().getTopInventory().getHolder() instanceof net.crafter.plugin.spigot.menu.ChestMenu chestMenu) {
                    chestMenu.refresh();
                }
            });
        });
        menuManager = new net.crafter.plugin.spigot.menu.MenuManager();
        
        loadLanguage();

        // Register listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(menuManager, this);
        SpigotCommandExecutor executor = new SpigotCommandExecutor(this);
        wsClient = new CrafterWebSocketClient(crafterConfig, executor, statsManager, playerManager, marketManager, languageManager);
        wsClient.setCommandStore(commandStore());
        wsClient.start();

        // PlaceholderAPI integration
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            if (new CrafterPlaceholderExpansion(this).register()) {
                getLogger().info("PlaceholderAPI expansion registered successfully.");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion!");
            }
        }

        // Update Checker & Expiration Check (İlk kontrol ve 6 saatte bir periyodik kontrol)
        runUpdateCheck();
        getServer().getScheduler().runTaskTimerAsynchronously(this, this::runUpdateCheck, 20L * 60 * 60 * 6, 20L * 60 * 60 * 6);

        getLogger().info("CrafterConnect active! Server ID: " + crafterConfig.getServerId());
    }

    private void runUpdateCheck() {
        new net.crafter.plugin.core.UpdateChecker(getDescription().getVersion()).checkForUpdates(result -> {
            this.updateResult = result;
            if (result.hasUpdate()) {
                if (result.isExpired()) {
                    this.isExpired = true;
                    if (wsClient != null) {
                        wsClient.shutdown();
                    }
                    getLogger().severe("====================================================");
                    getLogger().severe("[CrafterConnect] KRİTİK HATA: Kullandığınız sürüm zaman aşımına uğradı!");
                    getLogger().severe("[CrafterConnect] En son sürüm: " + result.getLatestVersion());
                    getLogger().severe("[CrafterConnect] Eklenti devre dışı bırakıldı. Lütfen yeni sürümü indirin:");
                    getLogger().severe("[CrafterConnect] " + result.getDownloadUrl());
                    getLogger().severe("====================================================");
                } else {
                    getLogger().warning("====================================================");
                    getLogger().warning("[CrafterConnect] DİKKAT: Yeni bir sürüm mevcut (" + result.getLatestVersion() + ")!");
                    getLogger().warning("[CrafterConnect] Yakında kullandığınız sürüm zaman aşımına uğrayacaktır.");
                    getLogger().warning("[CrafterConnect] İndirme adresi: " + result.getDownloadUrl());
                    getLogger().warning("====================================================");
                }
            }
        });
    }

    @Override
    public void onDisable() {
        if (wsClient != null) {
            wsClient.shutdown();
        }
        getLogger().info("CrafterConnect devre dışı.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Admin bilgilendirme
        if (player.hasPermission("crafter.admin")) {
            if (isExpired) {
                String latest = updateResult != null ? updateResult.getLatestVersion() : "";
                player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("version_expired", latest));
            } else if (updateResult != null && updateResult.hasUpdate()) {
                player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("version_warning", updateResult.getLatestVersion()));
            }
        }

        // Oyuncu girince offline kuyruğunu kontrol et ve backend'e bildir
        if (!isExpired && wsClient != null) {
            wsClient.onPlayerJoin(player.getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (playerManager != null) {
            playerManager.removePlayer(event.getPlayer().getName());
        }
        if (!isExpired && wsClient != null) {
            wsClient.onPlayerQuit(event.getPlayer().getName());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("crafter")) return false;
        
        if (isExpired) {
            String latest = updateResult != null ? updateResult.getLatestVersion() : "";
            sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("version_expired", latest));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> {
                if (!sender.hasPermission("crafter.admin")) {
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("no_permission"));
                    return true;
                }
                sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("status_title"));
                if (!crafterConfig.isConfigured()) {
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("status_info_line", languageManager.getMessage("status_not_configured")));
                } else {
                    boolean connected = wsClient != null && wsClient.isConnected();
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("status_info_line", (connected ? languageManager.getMessage("status_connected") : languageManager.getMessage("status_disconnected"))));
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("status_queue_line", (wsClient != null ? wsClient.getOfflineQueueSize() : 0)));
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("crafter.admin")) {
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("no_permission"));
                    return true;
                }
                try {
                    reloadConfig();
                    crafterConfig = loadCrafterConfig();
                    loadLanguage();
                    wsClient.shutdown();
                    SpigotCommandExecutor executor = new SpigotCommandExecutor(this);
                    wsClient = new CrafterWebSocketClient(crafterConfig, executor, statsManager, playerManager, marketManager, languageManager);
                    wsClient.setCommandStore(commandStore());
                    wsClient.start();
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("reloaded"));
                } catch (Exception e) {
                    sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("reload_error", e.getMessage()));
                }
            }
            case "magaza", "shop" -> {
                if (!(sender instanceof Player player)) return true;
                if (!player.hasPermission("crafter.shop")) {
                    player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("no_permission"));
                    return true;
                }
                if (!marketManager.isLoaded()) {
                    pendingShopPlayers.add(player.getName());
                    wsClient.getProducts(); 
                    return true;
                }
                
                if (args.length > 1) {
                    String catId = args[1];
                    if (marketManager.getCategories().stream().anyMatch(c -> c.getId().equalsIgnoreCase(catId))) {
                        menuManager.openMenu(player, new net.crafter.plugin.spigot.menu.ShopMenu(this, catId).getInventory());
                    } else {
                        player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("category_not_found", catId));
                    }
                } else {
                    menuManager.openMenu(player, new net.crafter.plugin.spigot.menu.CategoryMenu(this).getInventory());
                }
            }
            case "sandik", "chest" -> {
                if (!(sender instanceof Player player)) return true;
                if (!player.hasPermission("crafter.chest")) {
                    player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("no_permission"));
                    return true;
                }
                player.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("chest_opening"));
                wsClient.getChest(player.getName());
                menuManager.openMenu(player, new net.crafter.plugin.spigot.menu.ChestMenu(this, player).getInventory());
            }
            default -> sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("usage"));
        }

        return true;
    }

    private CrafterConfig loadCrafterConfig() {
        CrafterConfig cfg = new CrafterConfig();
        cfg.setApiUrl(getConfig().getString("api-url", "api.crafter.net.tr"));
        cfg.setWebsiteId(getConfig().getString("website-id", CrafterConfig.DEFAULT_WEBSITE_ID));
        cfg.setPluginSecret(getConfig().getString("plugin-secret", CrafterConfig.DEFAULT_PLUGIN_SECRET));
        cfg.setServerId(getConfig().getString("server-id", CrafterConfig.DEFAULT_SERVER_ID));
        cfg.setLanguage(getConfig().getString("language", "tr"));
        cfg.setReconnectDelaySeconds(getConfig().getInt("reconnect-delay-seconds", 5));
        cfg.setMaxReconnectAttempts(getConfig().getInt("max-reconnect-attempts", -1));
        cfg.setPingIntervalSeconds(getConfig().getInt("ping-interval-seconds", 30));
        cfg.setDebug(getConfig().getBoolean("debug-mode", getConfig().getBoolean("debug", false)));
        cfg.setUseSsl(getConfig().getBoolean("use-ssl", true));
        return cfg;
    }

    public void onMarketUpdate() {
        getServer().getScheduler().runTask(this, () -> {
            for (String playerName : new java.util.ArrayList<>(pendingShopPlayers)) {
                Player player = getServer().getPlayer(playerName);
                if (player != null && player.isOnline()) {
                    menuManager.openMenu(player, new net.crafter.plugin.spigot.menu.CategoryMenu(this).getInventory());
                }
            }
            pendingShopPlayers.clear();
        });
    }

    private void loadLanguage() {
        String lang = crafterConfig.getLanguage();
        File langFile = new File(getDataFolder(), "lang/" + lang + ".yml");
        if (!langFile.exists()) {
            saveResource("lang/" + lang + ".yml", false);
        }
        
        FileConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        java.util.Map<String, String> messages = new java.util.HashMap<>();
        ConfigurationSection section = langConfig.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                messages.put(key, section.getString(key));
            }
        }
        
        languageManager = new net.crafter.plugin.core.LanguageManager(lang);
        languageManager.setMessages(messages);
    }

    public net.crafter.plugin.core.LanguageManager getLanguageManager() { return languageManager; }

    public StatisticsManager getStatsManager() { return statsManager; }
    public net.crafter.plugin.core.PlayerManager getPlayerManager() { return playerManager; }
    public net.crafter.plugin.core.MarketManager getMarketManager() { return marketManager; }
    public CrafterWebSocketClient getWsClient() { return wsClient; }
    public boolean isExpired() { return isExpired; }
    public net.crafter.plugin.core.UpdateCheckResult getUpdateResult() { return updateResult; }


    /** Çevrimdışı oyuncu komutlarının kalıcı deposu (#273); tek örnek, reload'da da korunur. */
    private net.crafter.plugin.core.PendingCommandStore pendingCommandStore;

    private synchronized net.crafter.plugin.core.PendingCommandStore commandStore() {
        if (pendingCommandStore == null) {
            pendingCommandStore = new net.crafter.plugin.core.PendingCommandStore(new File(getDataFolder(), "pending-commands.json").toPath());
        }
        return pendingCommandStore;
    }
}
