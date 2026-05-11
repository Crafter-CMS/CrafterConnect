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
        version = "1.0.0",
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

        // Update Checker
        new net.crafter.plugin.core.UpdateChecker("1.0.0").checkForUpdates(latest -> {
            logger.warn("====================================================");
            logger.warn("A new version of CrafterConnect is available!");
            logger.warn("Latest: {}", latest);
            logger.warn("Download: https://github.com/Crafter-CMS/CrafterConnect/releases");
            logger.warn("====================================================");
        });

        logger.info("CrafterConnect active! Server ID: {}", crafterConfig.getServerId());
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
        // Oyuncu proxy'ye girince offline kuyruğunu kontrol et ve backend'e bildir
        if (wsClient != null) {
            wsClient.onPlayerJoin(event.getPlayer().getUsername());
        }
    }

    @Subscribe
    public void onDisconnect(com.velocitypowered.api.event.connection.DisconnectEvent event) {
        if (playerManager != null) {
            playerManager.removePlayer(event.getPlayer().getUsername());
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
        cfg.setApiUrl(node.node("api-url").getString("localhost:3000"));
        cfg.setWebsiteId(node.node("website-id").getString("YOUR_WEBSITE_ID"));
        cfg.setPluginSecret(node.node("plugin-secret").getString("YOUR_SECRET"));
        cfg.setServerId(node.node("server-id").getString("my-proxy-1"));
        cfg.setLanguage(node.node("language").getString("tr"));
        cfg.setReconnectDelaySeconds(node.node("reconnect-delay-seconds").getInt(5));
        cfg.setMaxReconnectAttempts(node.node("max-reconnect-attempts").getInt(-1));
        cfg.setPingIntervalSeconds(node.node("ping-interval-seconds").getInt(30));
        cfg.setDebug(node.node("debug").getBoolean(false));

        return cfg;

    }

    // Getters

    public CrafterWebSocketClient getWsClient() { return wsClient; }
    public CrafterConfig getCrafterConfig() { return crafterConfig; }
    public ProxyServer getServer() { return server; }
    public Logger getPluginLogger() { return logger; }
    public StatisticsManager getStatsManager() { return statsManager; }
}
