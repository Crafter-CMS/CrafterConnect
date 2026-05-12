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
        menuManager = new net.crafter.plugin.spigot.menu.MenuManager();
        
        loadLanguage();

        // Register listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(menuManager, this);
        SpigotCommandExecutor executor = new SpigotCommandExecutor(this);
        wsClient = new CrafterWebSocketClient(crafterConfig, executor, statsManager, playerManager, marketManager, languageManager);
        wsClient.start();

        // PlaceholderAPI integration
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            if (new CrafterPlaceholderExpansion(this).register()) {
                getLogger().info("PlaceholderAPI expansion registered successfully.");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion!");
            }
        }

        // Update Checker
        new net.crafter.plugin.core.UpdateChecker(getDescription().getVersion()).checkForUpdates(latest -> {
            getLogger().warning("====================================================");
            getLogger().warning(languageManager.getMessage("new_version_available"));
            getLogger().warning(languageManager.getMessage("current_version", getDescription().getVersion()));
            getLogger().warning(languageManager.getMessage("latest_version", latest));
            getLogger().warning(languageManager.getMessage("download_url", "https://github.com/Crafter-CMS/CrafterConnect/releases"));
            getLogger().warning("====================================================");
        });

        getLogger().info("CrafterConnect active! Server ID: " + crafterConfig.getServerId());
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
        // Oyuncu girince offline kuyruğunu kontrol et ve backend'e bildir
        if (wsClient != null) {
            wsClient.onPlayerJoin(event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (playerManager != null) {
            playerManager.removePlayer(event.getPlayer().getName());
        }
        if (wsClient != null) {
            wsClient.onPlayerQuit(event.getPlayer().getName());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("crafter")) return false;
        
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
                boolean connected = wsClient != null && wsClient.isConnected();
                sender.sendMessage(languageManager.getMessage("prefix") + languageManager.getMessage("status_title"));
                sender.sendMessage(languageManager.getMessage("status_info_line", (connected ? languageManager.getMessage("status_connected") : languageManager.getMessage("status_disconnected"))));
                sender.sendMessage(languageManager.getMessage("status_queue_line", (wsClient != null ? wsClient.getOfflineQueueSize() : 0)));
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
        cfg.setWebsiteId(getConfig().getString("website-id", "YOUR_WEBSITE_ID"));
        cfg.setPluginSecret(getConfig().getString("plugin-secret", "YOUR_SECRET"));
        cfg.setServerId(getConfig().getString("server-id", "my-server-1"));
        cfg.setLanguage(getConfig().getString("language", "tr"));
        cfg.setReconnectDelaySeconds(getConfig().getInt("reconnect-delay-seconds", 5));
        cfg.setMaxReconnectAttempts(getConfig().getInt("max-reconnect-attempts", -1));
        cfg.setPingIntervalSeconds(getConfig().getInt("ping-interval-seconds", 30));
        cfg.setDebug(getConfig().getBoolean("debug", false));
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

}
