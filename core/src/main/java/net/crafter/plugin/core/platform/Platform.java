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
 * Bindings that the Paper/Spigot, Velocity and BungeeCord modules provide to the core.
 */
public interface Platform extends CommandExecutor {

    /** Katalogdaki platform id'si: paper | spigot | velocity | bungeecord */
    String platformId();

    /** Minecraft (or proxy) version, e.g. "1.21.4" */
    String gameVersion();

    int onlinePlayers();

    int maxPlayers();

    /** Address the server listens on (display only, on the storefront), null if unknown */
    default String serverHost() { return null; }

    default int serverPort() { return 0; }

    /** plugins/CrafterConnect (Velocity'de plugins/crafter-connect) */
    Path dataDirectory();

    /** config.yml yolu */
    default Path configFile() {
        return dataDirectory().resolve("config.yml");
    }

    /** Copies the default config.yml if missing, then reads it */
    ConfigSource loadConfig() throws IOException;

    /** Returns messages by merging the bundled default language file with the user's file */
    Map<String, String> loadMessages(String language) throws IOException;

    // --- Playtime (#529): Paper/Spigot only; proxies do not send stats ---

    /** Whether the game's own playtime statistic (Statistic.PLAY_ONE_MINUTE) can be read */
    default boolean supportsPlayerStats() { return false; }

    /**
     * Reads online players' stats on each player's thread (their own region on Folia) and calls {@code done}
     * once all are done (from any thread).
     */
    default void collectPlayerStats(Consumer<List<PlayerSnapshot>> done) {
        done.accept(List.of());
    }

    /** UUIDs of online players (for the AFK poll; may be called from any thread) */
    default Collection<UUID> onlinePlayerIds() { return List.of(); }

    /**
     * AFK source: {@code mode} auto | essentials | cmi | idle | none; for idle the threshold is {@code idleThresholdMs}.
     * null: never counted as AFK.
     */
    default AfkSource afkSource(String mode, long idleThresholdMs) { return null; }

    /** Floodgate (varsa) */
    default FloodgateBridge floodgate() { return null; }

    CrafterLog log();
}
