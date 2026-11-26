package net.crafter.plugin.core.connect;

/** Eklentinin backend'e bildirdiği sunucu bilgisi (connect ve hello) */
public record ServerInfo(String platform, String pluginVersion, String gameVersion, int maxPlayers,
                         String host, int port, String floodgatePrefix) {}
