package net.crafter.plugin.core.playtime;

import java.util.UUID;

/** Whether the player is AFK right now (Essentials, CMI or the idle threshold) */
@FunctionalInterface
public interface AfkSource {
    boolean isAfk(UUID uuid, long nowMs);

    /** Never counted as AFK (playtime.afk-source: none) */
    AfkSource NONE = (uuid, now) -> false;
}
