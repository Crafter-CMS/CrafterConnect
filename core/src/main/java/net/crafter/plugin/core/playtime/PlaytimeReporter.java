package net.crafter.plugin.core.playtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.crafter.plugin.core.platform.CrafterLog;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * Playtime and AFK reporting (#529), scheduler-independent core.
 *
 * <ul>
 *   <li>{@link #record}: online players' {@code PLAY_ONE_MINUTE} values (every 5 minutes, on join and on quit)
 *   are turned into samples with the AFK total and written to the persistent queue.</li>
 *   <li>{@link #pollAfk}: polls AFK state at a short interval.</li>
 *   <li>{@link #flush}: pending samples are sent as {@code player_stats}; they stay queued until ACKed.
 *   If no ACK has been seen on this connection yet, only one packet (a probe) is sent; if no ACK arrives within 2 minutes
 *   the backend is assumed not to support player_stats and nothing is sent until reconnecting.</li>
 * </ul>
 */
public final class PlaytimeReporter {

    public static final int BATCH_SIZE = 200;
    static final long RESEND_AFTER_MS = 60_000;
    static final long PROBE_TIMEOUT_MS = 120_000;
    /** The AFK total of a player not seen for 180 days is removed (the backend works from the negative delta) */
    static final long AFK_RETENTION_MS = 180L * 24 * 60 * 60 * 1000;

    /** Writes the message to the socket; false if it could not be written */
    @FunctionalInterface
    public interface Sender {
        boolean send(JsonObject data);
    }

    private final PlaytimeStore store;
    private final AfkTracker afk;
    private final LongSupplier clock;
    private final CrafterLog log;
    private volatile boolean importHistory;

    private boolean ackSeen;
    private long probeSentAt;
    private boolean unsupportedLogged;

    public PlaytimeReporter(PlaytimeStore store, long afkPollMs, LongSupplier clock, CrafterLog log) {
        this.store = store;
        this.afk = new AfkTracker(afkPollMs);
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.log = log == null ? CrafterLog.silent() : log;
        afk.load(store.afkTotals(), this.clock.getAsLong() - AFK_RETENTION_MS);
    }

    public void setImportHistory(boolean importHistory) {
        this.importHistory = importHistory;
    }

    public void pollAfk(Collection<UUID> online, AfkSource source) {
        afk.poll(online, source, clock.getAsLong());
    }

    /** Writes player stats to the queue. reason: interval | join | quit */
    public void record(Collection<PlayerSnapshot> snapshots, String reason) {
        if (snapshots == null || snapshots.isEmpty()) return;
        long now = clock.getAsLong();
        List<StatSample> samples = new ArrayList<>();
        for (PlayerSnapshot p : snapshots) {
            if (p == null || p.uuid() == null) continue;
            long afkSeconds = Math.min(afk.totalSeconds(p.uuid()), Math.max(0, p.playSeconds()));
            samples.add(new StatSample(p.uuid().toString(), p.username(), Math.max(0, p.playSeconds()),
                    afkSeconds, now, now, reason));
            if ("quit".equals(reason)) afk.onQuit(p.uuid());
        }
        store.setAfkTotals(afk.export());
        store.add(samples);
    }

    /** New connection: ACK state is reset, pending samples may be retried */
    public synchronized void onConnected() {
        ackSeen = false;
        probeSentAt = 0;
        unsupportedLogged = false;
        for (StatSample s : store.pending()) s.sentAt = 0;
    }

    /** @return number of samples sent */
    public synchronized int flush(Sender sender) {
        if (sender == null) return 0;
        long now = clock.getAsLong();
        if (!ackSeen && probeSentAt > 0) {
            if (now - probeSentAt < PROBE_TIMEOUT_MS) return 0;
            if (!unsupportedLogged) {
                unsupportedLogged = true;
                log.debug("[CrafterConnect] Backend did not acknowledge player_stats; playtime samples stay queued.");
            }
            return 0;
        }
        int sent = 0;
        int limit = ackSeen ? Integer.MAX_VALUE : BATCH_SIZE;
        while (sent < limit) {
            List<StatSample> batch = store.due(now, RESEND_AFTER_MS, Math.min(BATCH_SIZE, limit - sent));
            if (batch.isEmpty()) break;
            if (!sender.send(message(batch))) break;
            store.markSent(batch, now);
            sent += batch.size();
            if (!ackSeen) {
                probeSentAt = now;
                break;
            }
        }
        return sent;
    }

    /** {@code player_stats_ack}: ACKed samples are removed; continues with {@code sender} if any are pending */
    public void onAck(Collection<String> keys, Sender sender) {
        synchronized (this) {
            ackSeen = true;
            probeSentAt = 0;
        }
        store.acknowledge(keys);
        flush(sender);
    }

    JsonObject message(List<StatSample> batch) {
        JsonObject data = new JsonObject();
        if (importHistory) data.addProperty("importHistory", true);
        JsonArray arr = new JsonArray();
        for (StatSample s : batch) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", s.uuid);
            if (s.username != null) o.addProperty("username", s.username);
            o.addProperty("playSeconds", s.playSeconds);
            o.addProperty("afkSeconds", s.afkSeconds);
            o.addProperty("lastSeen", s.lastSeen);
            o.addProperty("sampledAt", s.sampledAt);
            o.addProperty("reason", s.reason);
            arr.add(o);
        }
        data.add("samples", arr);
        return data;
    }

    public int queued() {
        return store.size();
    }

    public long afkSeconds(UUID uuid) {
        return afk.totalSeconds(uuid);
    }
}
