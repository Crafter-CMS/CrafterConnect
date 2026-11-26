package net.crafter.plugin.bungee;

import net.crafter.plugin.core.config.ConfigSource;
import net.crafter.plugin.core.platform.CrafterLog;
import net.crafter.plugin.core.platform.FloodgateBridge;
import net.crafter.plugin.core.platform.Platform;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * BungeeCord / Waterfall bağlantı noktaları. Velocity ile aynı davranır: komutlar proxy konsolunda, geliş sırasıyla
 * tek bir thread'de çalışır.
 */
public class BungeePlatform implements Platform {

    private final CrafterBungeePlugin plugin;
    private final CrafterLog log;
    private final ExecutorService commandThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "crafter-commands");
        t.setDaemon(true);
        return t;
    });

    public BungeePlatform(CrafterBungeePlugin plugin) {
        this.plugin = plugin;
        this.log = CrafterLog.ofJul(plugin.getLogger(), () -> plugin.getService() != null && plugin.getService().config().isDebug());
    }

    private ProxyServer proxy() {
        return plugin.getProxy();
    }

    @Override
    public void dispatchConsole(String command, Consumer<Boolean> callback) {
        commandThread.execute(() -> {
            boolean ok;
            try {
                ok = proxy().getPluginManager().dispatchCommand(proxy().getConsole(), command);
            } catch (Throwable t) {
                log.error("[CrafterConnect] Command execution error: " + t.getMessage());
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
        if (playerName == null || playerName.isBlank()) return false;
        String name = playerName.trim();
        if (proxy().getPlayer(name) != null) return true;
        // BungeeCord getPlayer(String) büyük/küçük harfe duyarlı olabilir
        for (ProxiedPlayer p : proxy().getPlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    @Override
    public boolean isPlayerOnline(UUID uuid) {
        return uuid != null && proxy().getPlayer(uuid) != null;
    }

    @Override
    public void sendMessage(String username, String message) {
        ProxiedPlayer p = proxy().getPlayer(username);
        if (p != null) p.sendMessage(TextComponent.fromLegacyText(message));
    }

    @Override
    public void handlePurchaseResponse(String username, boolean success, String message) {
        sendMessage(username, (success ? "§a✔ " : "§c✖ ") + message);
    }

    @Override
    public String getPlatformName() {
        return proxy().getName() + "/" + proxy().getVersion();
    }

    @Override
    public String platformId() {
        return "bungeecord";
    }

    @Override
    public String gameVersion() {
        return proxy().getVersion();
    }

    @Override
    public int onlinePlayers() {
        return proxy().getOnlineCount();
    }

    @Override
    public int maxPlayers() {
        Iterator<ListenerInfo> it = proxy().getConfig().getListeners().iterator();
        return it.hasNext() ? it.next().getMaxPlayers() : 0;
    }

    @Override
    public int serverPort() {
        Iterator<ListenerInfo> it = proxy().getConfig().getListeners().iterator();
        if (it.hasNext() && it.next().getSocketAddress() instanceof InetSocketAddress addr) return addr.getPort();
        return 0;
    }

    @Override
    public Path dataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public ConfigSource loadConfig() throws IOException {
        Files.createDirectories(dataDirectory());
        Path configPath = configFile();
        if (!Files.exists(configPath)) {
            try (InputStream in = plugin.getResourceAsStream("config.yml")) {
                if (in != null) Files.copy(in, configPath);
            }
        }
        Configuration c = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configPath.toFile());
        return new ConfigSource() {
            @Override public boolean has(String key) { return c.contains(key); }
            @Override public String getString(String key, String def) { return c.getString(key, def); }
            @Override public int getInt(String key, int def) { return c.getInt(key, def); }
            @Override public boolean getBoolean(String key, boolean def) { return c.getBoolean(key, def); }
            @Override public List<String> getStringList(String key) { return c.getStringList(key); }
        };
    }

    @Override
    public Map<String, String> loadMessages(String language) throws IOException {
        Map<String, String> messages = new HashMap<>();
        ConfigurationProvider yaml = ConfigurationProvider.getProvider(YamlConfiguration.class);
        String resource = "lang/" + language + ".yml";
        InputStream bundled = plugin.getResourceAsStream(resource);
        if (bundled == null) bundled = plugin.getResourceAsStream("lang/tr.yml");
        if (bundled != null) {
            try (InputStream in = bundled) {
                read(yaml.load(in), messages);
            }
        }
        Path langDir = dataDirectory().resolve("lang");
        Files.createDirectories(langDir);
        Path langPath = langDir.resolve(language + ".yml");
        if (!Files.exists(langPath)) {
            try (InputStream in = plugin.getResourceAsStream(resource)) {
                if (in != null) Files.copy(in, langPath);
            }
        }
        if (Files.exists(langPath)) read(yaml.load(langPath.toFile()), messages);
        return messages;
    }

    private static void read(Configuration root, Map<String, String> out) {
        Configuration section = root.getSection("messages");
        for (String key : section.getKeys()) {
            String v = section.getString(key, null);
            if (v != null) out.put(key, v);
        }
    }

    @Override
    public FloodgateBridge floodgate() {
        net.md_5.bungee.api.plugin.Plugin fg = proxy().getPluginManager().getPlugin("floodgate");
        return fg == null ? null : FloodgateBridge.detect(fg.getClass().getClassLoader());
    }

    @Override
    public CrafterLog log() {
        return log;
    }
}
