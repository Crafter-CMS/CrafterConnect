package net.crafter.plugin.core.playtime;

import java.util.Objects;

/** A single sample sent to the backend (player_stats). Key: uuid:sampledAt; the backend adds the server id. */
public final class StatSample {
    public String uuid;
    public String username;
    /** Statistic.PLAY_ONE_MINUTE, saniye (mutlak) */
    public long playSeconds;
    /** Total AFK counted by the plugin, seconds (absolute) */
    public long afkSeconds;
    public long lastSeen;
    public long sampledAt;
    /** interval | join | quit */
    public String reason;
    /** Last send (not written to disk) */
    transient long sentAt;

    public StatSample() {}

    public StatSample(String uuid, String username, long playSeconds, long afkSeconds, long lastSeen, long sampledAt, String reason) {
        this.uuid = uuid;
        this.username = username;
        this.playSeconds = playSeconds;
        this.afkSeconds = afkSeconds;
        this.lastSeen = lastSeen;
        this.sampledAt = sampledAt;
        this.reason = reason;
    }

    public String key() {
        return uuid + ":" + sampledAt;
    }

    @Override public boolean equals(Object o) {
        return o instanceof StatSample s && Objects.equals(key(), s.key());
    }

    @Override public int hashCode() {
        return key().hashCode();
    }
}
