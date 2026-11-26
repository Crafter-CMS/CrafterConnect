package net.crafter.plugin.core.playtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Essentials/CMI yoksa: oyuncu {@code thresholdMs} boyunca hareket etmez, yazmaz, komut kullanmazsa AFK sayılır.
 * Eşik süresinin kendisi aktif sayılır; AFK süresi eşik aşıldıktan sonra başlar. Thread güvenlidir.
 */
public final class IdleAfkSource implements AfkSource {

    private final long thresholdMs;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    public IdleAfkSource(long thresholdMs) {
        this.thresholdMs = Math.max(60_000L, thresholdMs);
    }

    /** Giriş, hareket (bakış dahil), sohbet, komut, etkileşim */
    public void markActive(UUID uuid, long nowMs) {
        if (uuid != null) lastActivity.put(uuid, nowMs);
    }

    public void forget(UUID uuid) {
        if (uuid != null) lastActivity.remove(uuid);
    }

    @Override
    public boolean isAfk(UUID uuid, long nowMs) {
        Long last = lastActivity.get(uuid);
        // Hiç etkinlik görülmediyse (eklenti yeniden yüklendi) şimdi başlamış sayılır
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
