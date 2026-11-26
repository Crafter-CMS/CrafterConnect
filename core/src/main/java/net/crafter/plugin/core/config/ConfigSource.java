package net.crafter.plugin.core.config;

import java.util.List;

/** Platformun YAML okuyucusu üzerinde ince bir katman (Bukkit, Configurate, BungeeCord config) */
public interface ConfigSource {
    /** Anahtar dosyada var mı */
    boolean has(String key);
    String getString(String key, String def);
    int getInt(String key, int def);
    boolean getBoolean(String key, boolean def);
    /** Yoksa boş liste */
    List<String> getStringList(String key);
}
