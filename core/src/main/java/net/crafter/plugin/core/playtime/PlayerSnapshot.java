package net.crafter.plugin.core.playtime;

import java.util.UUID;

/**
 * Player's in-game statistic (#529): {@code Statistic.PLAY_ONE_MINUTE} converted from ticks to seconds, ABSOLUTE value.
 * The game keeps this value in the player file; it survives a crash or restart (at most the ticks since the last save
 * are lost).
 */
public record PlayerSnapshot(UUID uuid, String username, long playSeconds) {

    /** 20 tick = 1 saniye */
    public static long ticksToSeconds(long ticks) {
        return Math.max(0, ticks) / 20L;
    }
}
