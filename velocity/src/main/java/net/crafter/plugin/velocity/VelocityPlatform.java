package net.crafter.plugin.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.crafter.plugin.core.config.ConfigSource;
import net.crafter.plugin.core.platform.CrafterLog;
import net.crafter.plugin.core.platform.FloodgateBridge;
import net.crafter.plugin.core.platform.Platform;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Velocity bindings. Commands run on the proxy console; for commands that must run on a backend server, the Paper
 * plugin is also installed on the backends and each connects as a separate CrafterConnect server.
 */
public class VelocityPlatform implements Platform {

    private final ProxyServer server;
    private final Path dataDirectory;
    private final CrafterLog log;
    /** Commands run one by one, in arrival order */
    private final ExecutorService commandThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "crafter-commands");
        t.setDaemon(true);
        return t;
    });

    public VelocityPlatform(ProxyServer server, Logger logger, Path dataDirectory, java.util.function.BooleanSupplier debug) {
        this.server = server;
        this.dataDirectory = dataDirectory;
        this.log = new CrafterLog() {
            @Override public void info(String m) { logger.info(m); }
            @Override public void warn(String m) { logger.warn(m); }
            @Override public void error(String m) { logger.error(m); }
            @Override public void debug(String m) { if (debug.getAsBoolean()) logger.info("[debug] {}", m); }
        };
    }

    @Override
    public void dispatchConsole(String command, Consumer<Boolean> callback) {
        commandThread.execute(() -> {
            boolean ok;
            try {
                ok = Boolean.TRUE.equals(server.getCommandManager()
                        .executeAsync(server.getConsoleCommandSource(), command)
                        .get(10, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("[CrafterConnect] Command execution error: " + e.getMessage());
                ok = false;
            }
            callback.accept(ok);
        });
    }

    void shutdown() {
        commandThread.shutdown();
    }

    @Override
    public boolean isPlayerOnline(String playerName) {
        // Velocity getPlayer(String) is case-insensitive
        return playerName != null && !playerName.isBlank() && server.getPlayer(playerName.trim()).isPresent();
    }

    @Override
    public boolean isPlayerOnline(UUID uuid) {
        return uuid != null && server.getPlayer(uuid).isPresent();
    }

    @Override
    public void sendMessage(String username, String message) {
        server.getPlayer(username).ifPresent(p -> p.sendMessage(legacy(message)));
    }

    @Override
    public void handlePurchaseResponse(String username, boolean success, String message) {
        Optional<Player> player = server.getPlayer(username);
        if (player.isEmpty()) return;
        // The language file lives in the core service; the message arrives ready to send
        player.get().sendMessage(legacy((success ? "§a✔ " : "§c✖ ") + message));
    }

    static net.kyori.adventure.text.Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    @Override
    public String getPlatformName() {
        return "Velocity/" + server.getVersion().getVersion();
    }

    @Override
    public String platformId() {
        return "velocity";
    }

    @Override
    public String gameVersion() {
        return server.getVersion().getVersion();
    }

    @Override
    public int onlinePlayers() {
        return server.getPlayerCount();
    }

    @Override
    public int maxPlayers() {
        return server.getConfiguration().getShowMaxPlayers();
    }

    @Override
    public String serverHost() {
        String host = server.getBoundAddress().getHostString();
        return host == null || host.equals("0.0.0.0") ? null : host;
    }

    @Override
    public int serverPort() {
        return server.getBoundAddress().getPort();
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public ConfigSource loadConfig() throws IOException {
        Files.createDirectories(dataDirectory);
        Path configPath = configFile();
        if (!Files.exists(configPath)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) Files.copy(in, configPath);
            }
        }
        ConfigurationNode node = YamlConfigurationLoader.builder().path(configPath).build().load();
        return new ConfigSource() {
            @Override public boolean has(String key) { return !node.node(key).virtual(); }
            @Override public String getString(String key, String def) { return node.node(key).getString(def); }
            @Override public int getInt(String key, int def) { return node.node(key).getInt(def); }
            @Override public boolean getBoolean(String key, boolean def) { return node.node(key).getBoolean(def); }
            @Override public List<String> getStringList(String key) {
                try {
                    List<String> l = node.node(key).getList(String.class);
                    return l == null ? new ArrayList<>() : l;
                } catch (Exception e) {
                    return new ArrayList<>();
                }
            }
        };
    }

    @Override
    public Map<String, String> loadMessages(String language) throws IOException {
        Map<String, String> messages = new HashMap<>();
        String resource = "/lang/" + language + ".yml";
        InputStream bundled = getClass().getResourceAsStream(resource);
        if (bundled == null) bundled = getClass().getResourceAsStream("/lang/tr.yml");
        if (bundled != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(bundled, StandardCharsets.UTF_8))) {
                read(YamlConfigurationLoader.builder().source(() -> r).build().load(), messages);
            }
        }
        Path langDir = dataDirectory.resolve("lang");
        Files.createDirectories(langDir);
        Path langPath = langDir.resolve(language + ".yml");
        if (!Files.exists(langPath)) {
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                if (in != null) Files.copy(in, langPath);
            }
        }
        if (Files.exists(langPath)) read(YamlConfigurationLoader.builder().path(langPath).build().load(), messages);
        return messages;
    }

    private static void read(ConfigurationNode root, Map<String, String> out) {
        ConfigurationNode section = root.node("messages");
        for (Map.Entry<Object, ? extends ConfigurationNode> e : section.childrenMap().entrySet()) {
            String v = e.getValue().getString();
            if (v != null) out.put(e.getKey().toString(), v);
        }
    }

    @Override
    public FloodgateBridge floodgate() {
        return server.getPluginManager().getPlugin("floodgate")
                .flatMap(c -> c.getInstance())
                .map(i -> FloodgateBridge.detect(i.getClass().getClassLoader()))
                .orElse(null);
    }

    @Override
    public CrafterLog log() {
        return log;
    }
}
