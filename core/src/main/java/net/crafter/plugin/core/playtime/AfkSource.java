package net.crafter.plugin.core.playtime;

import java.util.UUID;

/** Oyuncu şu an AFK mı (Essentials, CMI ya da hareketsizlik eşiği) */
@FunctionalInterface
public interface AfkSource {
    boolean isAfk(UUID uuid, long nowMs);

    /** AFK sayılmaz (playtime.afk-source: none) */
    AfkSource NONE = (uuid, now) -> false;
}
