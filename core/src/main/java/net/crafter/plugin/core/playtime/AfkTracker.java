package net.crafter.plugin.core.playtime;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Oyuncu başına toplam AFK süresi (saniye, MUTLAK; {@link PlaytimeStore} ile diske yazılır). Belirli aralıkla
 * {@link #poll} çağrılır; AFK olan oyuncuya son yoklamadan beri geçen süre eklenir. Yoklamalar arası boşluk
 * (sunucu donması, uyku) en çok {@code 2 × aralık} sayılır.
 */
public final class AfkTracker {

    private final long pollIntervalMs;
    private final Map<UUID, Long> totalMs = new HashMap<>();
    private final Map<UUID, Long> lastPoll = new HashMap<>();
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    public AfkTracker(long pollIntervalMs) {
        this.pollIntervalMs = Math.max(1_000L, pollIntervalMs);
    }

    public synchronized void poll(Collection<UUID> online, AfkSource source, long nowMs) {
        AfkSource src = source == null ? AfkSource.NONE : source;
        for (UUID uuid : online) {
            Long prev = lastPoll.put(uuid, nowMs);
            lastSeen.put(uuid, nowMs);
            if (prev == null) continue; // ilk yoklama: başlangıç
            long elapsed = Math.min(Math.max(0, nowMs - prev), 2 * pollIntervalMs);
            boolean afk;
            try {
                afk = src.isAfk(uuid, nowMs);
            } catch (RuntimeException e) {
                afk = false;
            }
            if (afk && elapsed > 0) totalMs.merge(uuid, elapsed, Long::sum);
        }
        // Çıkmış oyuncunun yoklama başlangıcı silinir (toplam kalır)
        lastPoll.keySet().retainAll(online);
    }

    /** Çıkışta: sonraki girişte AFK yeniden başlangıçtan sayılır */
    public synchronized void onQuit(UUID uuid) {
        lastPoll.remove(uuid);
    }

    public synchronized long totalSeconds(UUID uuid) {
        return totalMs.getOrDefault(uuid, 0L) / 1000L;
    }

    // --- Kalıcılık ---

    public synchronized Map<String, long[]> export() {
        Map<String, long[]> out = new HashMap<>();
        for (Map.Entry<UUID, Long> e : totalMs.entrySet()) {
            out.put(e.getKey().toString(), new long[]{e.getValue(), lastSeen.getOrDefault(e.getKey(), 0L)});
        }
        return out;
    }

    /** {@code staleBeforeMs}'den önce görülmemiş oyuncular yüklenmez (dosya sınırsız büyümez) */
    public synchronized void load(Map<String, long[]> saved, long staleBeforeMs) {
        if (saved == null) return;
        for (Map.Entry<String, long[]> e : saved.entrySet()) {
            long[] v = e.getValue();
            if (v == null || v.length < 1) continue;
            long seen = v.length > 1 ? v[1] : 0;
            if (seen > 0 && seen < staleBeforeMs) continue;
            try {
                UUID uuid = UUID.fromString(e.getKey());
                totalMs.put(uuid, Math.max(0, v[0]));
                if (seen > 0) lastSeen.put(uuid, seen);
            } catch (IllegalArgumentException ignored) {
                // bozuk anahtar atlanır
            }
        }
    }
}
