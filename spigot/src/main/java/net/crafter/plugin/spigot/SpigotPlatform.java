package net.crafter.plugin.spigot;

import net.crafter.plugin.core.LanguageManager;
import net.crafter.plugin.core.config.ConfigSource;
import net.crafter.plugin.core.platform.CrafterLog;
import net.crafter.plugin.core.platform.FloodgateBridge;
import net.crafter.plugin.core.platform.Platform;
import net.crafter.plugin.core.playtime.AfkSource;
import net.crafter.plugin.core.playtime.PlayerSnapshot;
import net.crafter.plugin.core.playtime.SnapshotBatch;
import org.bukkit.Statistic;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Paper/Spigot (ve Folia) bağlantı noktaları */
public class SpigotPlatform implements Platform {

    private final CrafterSpigotPlugin plugin;
    private final SpigotScheduler scheduler;
    private final CrafterLog log;

    public SpigotPlatform(CrafterSpigotPlugin plugin, SpigotScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.log = CrafterLog.ofJul(plugin.getLogger(), () -> plugin.getService() != null && plugin.getService().config().isDebug());
    }

    @Override
    public void dispatchConsole(String command, Consumer<Boolean> callback) {
        scheduler.runGlobal(() -> {
            boolean ok;
            try {
                ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Throwable t) {
                log.error("[CrafterConnect] Command execution error: " + t.getMessage());
                ok = false;
            }
            callback.accept(ok);
        });
    }

    @Override
    public boolean isPlayerOnline(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        // getPlayerExact büyük/küçük harf duyarsızdır
        Player p = Bukkit.getPlayerExact(playerName.trim());
        return p != null && p.isOnline();
    }

    @Override
    public boolean isPlayerOnline(UUID uuid) {
        Player p = uuid == null ? null : Bukkit.getPlayer(uuid);
        return p != null && p.isOnline();
    }

    @Override
    public void sendMessage(String username, String message) {
        Player p = Bukkit.getPlayerExact(username);
        if (p != null) scheduler.runForPlayer(p, () -> p.sendMessage(message));
    }

    @Override
    public void handlePurchaseResponse(String username, boolean success, String message) {
        Player player = Bukkit.getPlayerExact(username);
        if (player == null) return;
        scheduler.runForPlayer(player, () -> SpigotMessages.purchaseResponse(plugin, player, success, message));
    }

    @Override
    public String getPlatformName() {
        return (scheduler.isFolia() ? "Folia/" : "") + Bukkit.getName() + "/" + Bukkit.getVersion();
    }

    @Override
    public String platformId() {
        // Katalogda Folia ayrı platform değildir; Paper ve türevleri "paper"
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
            return "paper";
        } catch (ClassNotFoundException ignored) {
            // eski Paper sınıfı yoksa yenisini dene
        }
        try {
            Class.forName("io.papermc.paper.configuration.Configuration");
            return "paper";
        } catch (ClassNotFoundException ignored) {
            return "spigot";
        }
    }

    @Override
    public String gameVersion() {
        String v = Bukkit.getBukkitVersion(); // "1.21.4-R0.1-SNAPSHOT"
        int dash = v.indexOf('-');
        return dash > 0 ? v.substring(0, dash) : v;
    }

    @Override
    public int onlinePlayers() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public int maxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    @Override
    public String serverHost() {
        String ip = Bukkit.getIp();
        return ip == null || ip.isBlank() ? null : ip;
    }

    @Override
    public int serverPort() {
        return Bukkit.getPort();
    }

    @Override
    public Path dataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    @Override
    public ConfigSource loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
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
        // Paketteki varsayılanlar: yeni sürümde eklenen anahtarlar eski dil dosyasında yoksa buradan gelir
        String resource = "lang/" + language + ".yml";
        InputStream bundled = plugin.getResource(resource);
        if (bundled == null) {
            resource = "lang/tr.yml";
            bundled = plugin.getResource(resource);
        }
        if (bundled != null) {
            try (InputStreamReader r = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
                read(YamlConfiguration.loadConfiguration(r), messages);
            }
        }
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (!file.exists() && plugin.getResource("lang/" + language + ".yml") != null) {
            plugin.saveResource("lang/" + language + ".yml", false);
        }
        if (file.exists()) read(YamlConfiguration.loadConfiguration(file), messages);
        return messages;
    }

    private static void read(FileConfiguration yaml, Map<String, String> out) {
        ConfigurationSection section = yaml.getConfigurationSection("messages");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            String v = section.getString(key);
            if (v != null) out.put(key, v);
        }
    }

    @Override
    public FloodgateBridge floodgate() {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null) return null;
        return FloodgateBridge.detect(Bukkit.getPluginManager().getPlugin("floodgate").getClass().getClassLoader());
    }

    @Override
    public CrafterLog log() {
        return log;
    }

    // --- Oynama süresi (#529) ---

    @Override
    public boolean supportsPlayerStats() {
        return true;
    }

    /** Oyuncunun thread'inde çağrılmalı (Paper/Spigot: ana thread; Folia: oyuncunun bölgesi) */
    static PlayerSnapshot snapshot(Player player) {
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE); // adı yanıltıcı: tick sayar
        return new PlayerSnapshot(player.getUniqueId(), player.getName(), PlayerSnapshot.ticksToSeconds(ticks));
    }

    @Override
    public void collectPlayerStats(Consumer<List<PlayerSnapshot>> done) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            done.accept(List.of());
            return;
        }
        if (!scheduler.isFolia()) {
            scheduler.runGlobal(() -> {
                List<PlayerSnapshot> out = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        out.add(snapshot(p));
                    } catch (RuntimeException e) {
                        log.debug("[CrafterConnect] Could not read statistic for " + p.getName() + ": " + e.getMessage());
                    }
                }
                done.accept(out);
            });
            return;
        }
        // Folia: her oyuncu kendi bölgesinde okunur; hepsi (ya da çıkanlar) bitince toplu teslim
        SnapshotBatch batch = new SnapshotBatch(players.size(), done);
        for (Player p : players) {
            try {
                scheduler.runForPlayer(p, () -> {
                    PlayerSnapshot snap = null;
                    try {
                        snap = snapshot(p);
                    } catch (RuntimeException e) {
                        log.debug("[CrafterConnect] Could not read statistic for " + p.getName() + ": " + e.getMessage());
                    }
                    batch.add(snap);
                }, batch::skip);
            } catch (RuntimeException e) {
                batch.skip();
            }
        }
    }

    @Override
    public Collection<UUID> onlinePlayerIds() {
        List<UUID> ids = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) ids.add(p.getUniqueId());
        return ids;
    }

    @Override
    public AfkSource afkSource(String mode, long idleThresholdMs) {
        return plugin.afk().select(mode, idleThresholdMs);
    }

    /** LanguageManager kısayolu */
    LanguageManager lang() {
        return plugin.getLanguageManager();
    }
}
