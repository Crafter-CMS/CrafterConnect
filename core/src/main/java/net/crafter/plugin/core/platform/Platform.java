package net.crafter.plugin.core.platform;

import net.crafter.plugin.core.CommandExecutor;
import net.crafter.plugin.core.config.ConfigSource;
import net.crafter.plugin.core.playtime.AfkSource;
import net.crafter.plugin.core.playtime.PlayerSnapshot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Paper/Spigot, Velocity ve BungeeCord modüllerinin çekirdeğe sağladığı bağlantı noktaları.
 */
public interface Platform extends CommandExecutor {

    /** Katalogdaki platform id'si: paper | spigot | velocity | bungeecord */
    String platformId();

    /** Minecraft (ya da proxy) sürümü, örn. "1.21.4" */
    String gameVersion();

    int onlinePlayers();

    int maxPlayers();

    /** Sunucunun dinlediği adres (yalnızca vitrinde gösterim için), bilinmiyorsa null */
    default String serverHost() { return null; }

    default int serverPort() { return 0; }

    /** plugins/CrafterConnect (Velocity'de plugins/crafter-connect) */
    Path dataDirectory();

    /** config.yml yolu */
    default Path configFile() {
        return dataDirectory().resolve("config.yml");
    }

    /** config.yml yoksa varsayılanı kopyalar ve okur */
    ConfigSource loadConfig() throws IOException;

    /** Paketteki varsayılan dil dosyası ile kullanıcının dosyasını birleştirerek mesajları verir */
    Map<String, String> loadMessages(String language) throws IOException;

    // --- Oynama süresi (#529): yalnızca Paper/Spigot; proxy'ler istatistik göndermez ---

    /** Oyunun kendi oynama istatistiği (Statistic.PLAY_ONE_MINUTE) okunabiliyor mu */
    default boolean supportsPlayerStats() { return false; }

    /**
     * Çevrimiçi oyuncuların istatistiğini oyuncunun thread'inde (Folia'da kendi bölgesinde) okur ve hepsi bitince
     * {@code done}'ı çağırır (herhangi bir thread'den).
     */
    default void collectPlayerStats(Consumer<List<PlayerSnapshot>> done) {
        done.accept(List.of());
    }

    /** Çevrimiçi oyuncuların UUID'leri (AFK yoklaması için; herhangi bir thread'den çağrılabilir) */
    default Collection<UUID> onlinePlayerIds() { return List.of(); }

    /**
     * AFK kaynağı: {@code mode} auto | essentials | cmi | idle | none; idle için eşik {@code idleThresholdMs}.
     * null: AFK sayılmaz.
     */
    default AfkSource afkSource(String mode, long idleThresholdMs) { return null; }

    /** Floodgate (varsa) */
    default FloodgateBridge floodgate() { return null; }

    CrafterLog log();
}
