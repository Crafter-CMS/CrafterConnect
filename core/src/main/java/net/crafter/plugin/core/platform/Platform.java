package net.crafter.plugin.core.platform;

import net.crafter.plugin.core.CommandExecutor;
import net.crafter.plugin.core.config.ConfigSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

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

    /** Floodgate (varsa) */
    default FloodgateBridge floodgate() { return null; }

    CrafterLog log();
}
