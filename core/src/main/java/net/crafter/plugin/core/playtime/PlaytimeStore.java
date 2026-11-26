package net.crafter.plugin.core.playtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.crafter.plugin.core.config.SecureFiles;
import net.crafter.plugin.core.platform.CrafterLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Oynama süresi örneklerinin kalıcı giden kutusu ({@code plugins/CrafterConnect/data/playtime.json}, #529).
 * Komut ACK kuyruğuyla aynı kalıp: bağlantı yokken biriken örnekler sunucu yeniden başlasa da kaybolmaz, backend
 * ACK'leyince silinir. Değerler mutlak olduğundan tekrar gönderim süreyi artırmaz; kuyruk dolarsa en eski örnekler
 * düşer (toplam kaybolmaz, yalnızca günlük dağılım kabalaşır). AFK toplamları da burada saklanır.
 */
public class PlaytimeStore {

    public static final int MAX_SAMPLES = 10_000;

    private final Path file;
    private final CrafterLog log;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final List<StatSample> outbox = new ArrayList<>();
    private final Map<String, Long> lastSampleAt = new HashMap<>();
    private Map<String, long[]> afk = new HashMap<>();

    private static final class Snapshot {
        int version = 1;
        List<StatSample> outbox;
        Map<String, long[]> afk;
    }

    /** @param file null ise yalnızca bellekte (testler) */
    public PlaytimeStore(Path file, CrafterLog log) {
        this.file = file;
        this.log = log == null ? CrafterLog.silent() : log;
        load();
    }

    /** Örnekleri ekler. Aynı oyuncunun örnek zamanı hep artar (aynı milisaniyede iki örnek aynı anahtarı almaz). */
    public synchronized void add(Collection<StatSample> samples) {
        if (samples.isEmpty()) return;
        for (StatSample s : samples) {
            Long last = lastSampleAt.get(s.uuid);
            if (last != null && s.sampledAt <= last) s.sampledAt = last + 1;
            lastSampleAt.put(s.uuid, s.sampledAt);
            outbox.add(s);
        }
        int overflow = outbox.size() - MAX_SAMPLES;
        if (overflow > 0) {
            outbox.subList(0, overflow).clear();
            log.warn("[CrafterConnect] Playtime queue is full; the oldest " + overflow + " samples were dropped.");
        }
        save();
    }

    /** Gönderilecekler: hiç gönderilmemiş ya da {@code resendAfterMs}'den önce gönderilip ACK'lenmemiş */
    public synchronized List<StatSample> due(long nowMs, long resendAfterMs, int limit) {
        List<StatSample> out = new ArrayList<>();
        for (StatSample s : outbox) {
            if (out.size() >= limit) break;
            if (s.sentAt == 0 || nowMs - s.sentAt >= resendAfterMs) out.add(s);
        }
        return out;
    }

    public synchronized void markSent(Collection<StatSample> samples, long nowMs) {
        for (StatSample s : samples) s.sentAt = nowMs;
    }

    public synchronized int acknowledge(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return 0;
        Set<String> set = new HashSet<>(keys);
        int before = outbox.size();
        outbox.removeIf(s -> set.contains(s.key()));
        int removed = before - outbox.size();
        if (removed > 0) save();
        return removed;
    }

    public synchronized int size() {
        return outbox.size();
    }

    public synchronized List<StatSample> pending() {
        return new ArrayList<>(outbox);
    }

    public synchronized Map<String, long[]> afkTotals() {
        return new HashMap<>(afk);
    }

    public synchronized void setAfkTotals(Map<String, long[]> totals) {
        afk = totals == null ? new HashMap<>() : new HashMap<>(totals);
        save();
    }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            Snapshot snap = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Snapshot.class);
            if (snap == null) return;
            if (snap.outbox != null) {
                for (StatSample s : snap.outbox) {
                    if (s == null || s.uuid == null) continue;
                    outbox.add(s);
                    lastSampleAt.merge(s.uuid, s.sampledAt, Math::max);
                }
            }
            if (snap.afk != null) afk = new HashMap<>(snap.afk);
        } catch (Exception e) {
            log.error("[CrafterConnect] Could not read playtime store: " + e.getMessage());
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()));
            } catch (IOException ignored) {
                // kopya alınamazsa devam
            }
        }
    }

    private void save() {
        if (file == null) return;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) SecureFiles.ensurePrivateDirectory(parent);
            Snapshot snap = new Snapshot();
            snap.outbox = outbox;
            snap.afk = afk;
            SecureFiles.writeAtomically(file, gson.toJson(snap));
        } catch (IOException e) {
            log.error("[CrafterConnect] Could not write playtime store: " + e.getMessage());
        }
    }
}
