package net.crafter.plugin.core.config;

import java.util.List;

/** Thin layer over the platform's YAML reader (Bukkit, Configurate, BungeeCord config) */
public interface ConfigSource {
    /** Whether the key exists in the file */
    boolean has(String key);
    String getString(String key, String def);
    int getInt(String key, int def);
    boolean getBoolean(String key, boolean def);
    /** Empty list if missing */
    List<String> getStringList(String key);
}
