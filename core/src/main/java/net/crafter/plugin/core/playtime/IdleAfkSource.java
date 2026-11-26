package net.crafter.plugin.core.playtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Without Essentials/CMI: a player counts as AFK if they do not move, chat or run a command for {@code thresholdMs}.
 * The threshold period itself counts as active; AFK time starts after the threshold is exceeded. Thread-safe.
 */
public final class IdleAfkSource implements AfkSource {

    private final long thresholdMs;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    public IdleAfkSource(long thresholdMs) {
        this.thresholdMs = Math.max(60_000L, thresholdMs);
    }

    /** Join, movement (including look), chat, command, interaction */
    public void markActive(UUID uuid, long nowMs) {
        if (uuid != null) lastActivity.put(uuid, nowMs);
    }

    public void forget(UUID uuid) {
        if (uuid != null) lastActivity.remove(uuid);
    }

    @Override
    public boolean isAfk(UUID uuid, long nowMs) {
        Long last = lastActivity.get(uuid);
        // If no activity was ever seen (plugin reloaded), treat it as starting now
        if (last == null) {
            lastActivity.put(uuid, nowMs);
            return false;
        }
        return nowMs - last >= thresholdMs;
    }

    public long thresholdMs() {
        return thresholdMs;
    }
}
