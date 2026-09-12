package net.crafter.plugin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.crafter.plugin.core.StatisticsManager;
import net.crafter.plugin.core.config.CrafterConfig;
import net.crafter.plugin.core.websocket.CrafterWebSocketClient;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;

/**
 * Velocity proxy plugin ana sınıfı.
 *
 * NOT: Velocity genellikle proxy katmanında kullanılır.
 * Asıl item delivery için her backend sunucuya da Spigot plugin kurulmalıdır.
 * Bu plugin proxy-seviyesinde komutlar veya ağ genelinde oyuncu kontrolü için kullanılır.
 */
@Plugin(
        id = "crafter-connect",
        name = "CrafterConnect",
        version = "1.0.5",
        description = "crafter.net.tr satın alım delivery plugin",
        url = "https://crafter.net.tr",
        authors = {"crafter.net.tr"}
)
public class CrafterVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private CrafterWebSocketClient wsClient;
    private CrafterConfig crafterConfig;
    private StatisticsManager statsManager;
    private net.crafter.plugin.core.PlayerManager playerManager;
    private net.crafter.plugin.core.MarketManager marketManager;
    private net.crafter.plugin.core.LanguageManager languageManager;
    private volatile boolean isExpired = false;
    private volatile net.crafter.plugin.core.UpdateCheckResult updateResult = null;

    @Inject
    public CrafterVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("=== CrafterConnect (Velocity) is starting ===");

        try {
            crafterConfig = loadConfig();
            loadLanguage();
        } catch (IOException e) {
            logger.error("Failed to load config: {}", e.getMessage());
            return;
        }

        statsManager = new StatisticsManager();
        playerManager = new net.crafter.plugin.core.PlayerManager();
        marketManager = new net.crafter.plugin.core.MarketManager();
        languageManager = new net.crafter.plugin.core.LanguageManager(crafterConfig.getLanguage());
        
        VelocityCommandExecutor executor = new VelocityCommandExecutor(server, logger, this);
        wsClient = new CrafterWebSocketClient(crafterConfig, executor, statsManager, playerManager, marketManager, languageManager);
        wsClient.start();

        // Komut kaydı
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("crafter").build(),
                new CrafterVelocityCommand(this)
        );

        // Update Checker & Expiration Check (İlk kontrol ve 6 saatte bir periyodik kontrol)
        runUpdateCheck();
        server.getScheduler().buildTask(this, this::runUpdateCheck)
                .repeat(6, TimeUnit.HOURS)
                .schedule();

        logger.info("CrafterConnect active! Server ID: {}", crafterConfig.getServerId());
    }

    private void runUpdateCheck() {
        String currentVer = server.getPluginManager().getPlugin("crafter-connect")
                .flatMap(c -> c.getDescription().getVersion())
                .orElse("1.0.5");
        new net.crafter.plugin.core.UpdateChecker(currentVer).checkForUpdates(result -> {
            this.updateResult = result;
            if (result.hasUpdate()) {
                if (result.isExpired()) {
                    this.isExpired = true;
                    if (wsClient != null) {
                        wsClient.shutdown();
                    }
                    logger.error("====================================================");
                    logger.error("[CrafterConnect] KRİTİK HATA: Kullandığınız sürüm zaman aşımına uğradı!");
                    logger.error("[CrafterConnect] En son sürüm: {}", result.getLatestVersion());
                    logger.error("[CrafterConnect] Eklenti devre dışı bırakıldı. Lütfen yeni sürümü indirin:");
                    logger.error("[CrafterConnect] {}", result.getDownloadUrl());
                    logger.error("====================================================");
                } else {
                    logger.warn("====================================================");
                    logger.warn("[CrafterConnect] DİKKAT: Yeni bir sürüm mevcut ({})!", result.getLatestVersion());
                    logger.warn("[CrafterConnect] Yakında kullandığınız sürüm zaman aşımına uğrayacaktır.");
                    logger.warn("[CrafterConnect] İndirme adresi: {}", result.getDownloadUrl());
                    logger.warn("====================================================");
                }
            }
        });
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (wsClient != null) {
            wsClient.shutdown();
        }
        logger.info("CrafterConnect disabled.");
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        var player = event.getPlayer();
        if (player.hasPermission("crafter.admin")) {
            if (isExpired) {
                String latest = updateResult != null ? updateResult.getLatestVersion() : "";
                player.sendMessage(Component.text(languageManager.getMessage("prefix") + languageManager.getMessage("version_expired", latest)));
            } else if (updateResult != null && updateResult.hasUpdate()) {
                player.sendMessage(Component.text(languageManager.getMessage("prefix") + languageManager.getMessage("version_warning", updateResult.getLatestVersion())));
            }
        }

        // Oyuncu proxy'ye girince offline kuyruğunu kontrol et ve backend'e bildir
        if (!isExpired && wsClient != null) {
            wsClient.onPlayerJoin(player.getUsername());
        }
    }

    public void reload() throws IOException {
        crafterConfig = loadConfig();
        loadLanguage();
        if (wsClient != null) {
            wsClient.shutdown();
        }
        VelocityCommandExecutor executor = new VelocityCommandExecutor(server, logger, this);
        wsClient = new CrafterWebSocketClient(crafterConfig, executor, statsManager, playerManager, marketManager, languageManager);
        wsClient.start();
    }

    @Subscribe
    public void onDisconnect(com.velocitypowered.api.event.connection.DisconnectEvent event) {
        if (playerManager != null) {
            playerManager.removePlayer(event.getPlayer().getUsername());
        }
        if (!isExpired && wsClient != null) {
            wsClient.onPlayerQuit(event.getPlayer().getUsername());
        }
    }

    private void loadLanguage() throws IOException {
        String lang = crafterConfig.getLanguage();
        Path langDir = dataDirectory.resolve("lang");
        if (!Files.exists(langDir)) {
            Files.createDirectories(langDir);
        }

        Path langPath = langDir.resolve(lang + ".yml");
        if (!Files.exists(langPath)) {
            try (InputStream in = getClass().getResourceAsStream("/lang/" + lang + ".yml")) {
                if (in != null) {
                    Files.copy(in, langPath);
                } else {
                    // Eğer resource'da yoksa ve tr/en ise oluştur
                    if (lang.equals("tr") || lang.equals("en")) {
                         // Default loading from resource if possible or fallback
                    }
                }
            }
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(langPath)
                .build();
        ConfigurationNode node = loader.load();
        
        java.util.Map<String, String> messages = new java.util.HashMap<>();
        flattenNode("", node, messages);
        
        if (languageManager == null) {
            languageManager = new net.crafter.plugin.core.LanguageManager(lang);
        }
        languageManager.setMessages(messages);
    }

    private void flattenNode(String path, ConfigurationNode node, java.util.Map<String, String> messages) {
        if (node.isMap()) {
            for (java.util.Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
                String newPath = path.isEmpty() ? entry.getKey().toString() : path + "." + entry.getKey().toString();
                flattenNode(newPath, entry.getValue(), messages);
            }
        } else if (node.raw() != null) {
            messages.put(path, node.getString());
        }
    }

    private CrafterConfig loadConfig() throws IOException {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.exists(configPath)) {
            // Varsayılan config'i kopyala
            try (InputStream in = getClass().getResourceAsStream("/velocity-config.yml")) {
                if (in != null) Files.copy(in, configPath);
            }
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .build();

        ConfigurationNode node = loader.load();

        CrafterConfig cfg = new CrafterConfig();
        cfg.setApiUrl(node.node("api-url").getString("api.crafter.net.tr"));
        cfg.setWebsiteId(node.node("website-id").getString(net.crafter.plugin.core.config.CrafterConfig.DEFAULT_WEBSITE_ID));
        cfg.setPluginSecret(node.node("plugin-secret").getString(net.crafter.plugin.core.config.CrafterConfig.DEFAULT_PLUGIN_SECRET));
        cfg.setServerId(node.node("server-id").getString(net.crafter.plugin.core.config.CrafterConfig.DEFAULT_SERVER_ID));
        cfg.setLanguage(node.node("language").getString("tr"));
        cfg.setReconnectDelaySeconds(node.node("reconnect-delay-seconds").getInt(5));
        cfg.setMaxReconnectAttempts(node.node("max-reconnect-attempts").getInt(-1));
        cfg.setPingIntervalSeconds(node.node("ping-interval-seconds").getInt(30));
        cfg.setDebug(node.node("debug").getBoolean(false));
        cfg.setUseSsl(node.node("use-ssl").getBoolean(true));

        return cfg;

    }

    // Getters

    public net.crafter.plugin.core.LanguageManager getLanguageManager() { return languageManager; }
    public CrafterWebSocketClient getWsClient() { return wsClient; }
    public CrafterConfig getCrafterConfig() { return crafterConfig; }
    public ProxyServer getServer() { return server; }
    public Logger getPluginLogger() { return logger; }
    public StatisticsManager getStatsManager() { return statsManager; }
    public boolean isExpired() { return isExpired; }
    public net.crafter.plugin.core.UpdateCheckResult getUpdateResult() { return updateResult; }
}
