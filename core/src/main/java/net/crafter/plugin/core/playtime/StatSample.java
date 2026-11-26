package net.crafter.plugin.core.playtime;

import java.util.Objects;

/** Backend'e giden tek örnek (player_stats). Anahtar: uuid:sampledAt; backend sunucu id'sini ekler. */
public final class StatSample {
    public String uuid;
    public String username;
    /** Statistic.PLAY_ONE_MINUTE, saniye (mutlak) */
    public long playSeconds;
    /** Eklentinin saydığı toplam AFK, saniye (mutlak) */
    public long afkSeconds;
    public long lastSeen;
    public long sampledAt;
    /** interval | join | quit */
    public String reason;
    /** Son gönderim (diske yazılmaz) */
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
