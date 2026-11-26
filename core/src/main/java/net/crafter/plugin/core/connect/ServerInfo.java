package net.crafter.plugin.core.connect;

/** Server info the plugin reports to the backend (connect and hello) */
public record ServerInfo(String platform, String pluginVersion, String gameVersion, int maxPlayers,
                         String host, int port, String floodgatePrefix) {}
