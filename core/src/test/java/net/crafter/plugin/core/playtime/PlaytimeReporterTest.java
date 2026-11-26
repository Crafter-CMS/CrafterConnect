package net.crafter.plugin.core.playtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PlaytimeReporterTest {

    static final UUID A = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir Path dir;

    final AtomicLong clock = new AtomicLong(1_764_000_000_000L);
    final List<JsonObject> sent = new ArrayList<>();
    final PlaytimeReporter.Sender online = data -> { sent.add(data); return true; };
    final PlaytimeReporter.Sender offline = data -> false;

    PlaytimeReporter reporter(Path file) {
        return new PlaytimeReporter(new PlaytimeStore(file, null), 30_000, clock::get, null);
    }

    static List<String> keys(JsonObject msg) {
        List<String> out = new ArrayList<>();
        JsonArray arr = msg.getAsJsonArray("samples");
        arr.forEach(e -> out.add(e.getAsJsonObject().get("uuid").getAsString() + ":" + e.getAsJsonObject().get("sampledAt").getAsLong()));
        return out;
    }

    @Test
    void ticksAreConvertedToSeconds() {
        assertEquals(3600, PlayerSnapshot.ticksToSeconds(72_000));
        assertEquals(0, PlayerSnapshot.ticksToSeconds(-5));
    }

    @Test
    void absoluteSampleWithAfkIsQueuedUntilAcked() {
        PlaytimeReporter r = reporter(null);
        r.pollAfk(List.of(A), (u, n) -> true);
        clock.addAndGet(30_000);
        r.pollAfk(List.of(A), (u, n) -> true);
        r.record(List.of(new PlayerSnapshot(A, "Oyuncu1", 3600)), "interval");

        assertEquals(1, r.flush(online));
        JsonObject s = sent.get(0).getAsJsonArray("samples").get(0).getAsJsonObject();
        assertEquals(3600, s.get("playSeconds").getAsLong());
        assertEquals(30, s.get("afkSeconds").getAsLong());
        assertEquals("interval", s.get("reason").getAsString());
        assertFalse(sent.get(0).has("importHistory"));
        assertEquals(1, r.queued(), "kept until ack");

        r.onAck(keys(sent.get(0)), online);
        assertEquals(0, r.queued());
    }

    @Test
    void offlineSamplesSurviveRestartAndAreSentOnReconnect() {
        Path file = dir.resolve("data/playtime.json");
        PlaytimeReporter r = reporter(file);
        r.record(List.of(new PlayerSnapshot(A, "Oyuncu1", 100)), "join");
        clock.addAndGet(300_000);
        r.record(List.of(new PlayerSnapshot(A, "Oyuncu1", 400)), "interval");
        assertEquals(0, r.flush(offline));
        assertTrue(Files.exists(file));

        PlaytimeReporter restarted = reporter(file); // server crashed / restarted
        assertEquals(2, restarted.queued());
        restarted.onConnected();
        assertEquals(2, restarted.flush(online));
        restarted.onAck(keys(sent.get(0)), online);
        assertEquals(0, reporter(file).queued());
    }

    @Test
    void sameMillisecondSamplesGetDistinctKeys() {
        PlaytimeReporter r = reporter(null);
        r.record(List.of(new PlayerSnapshot(A, "Oyuncu1", 100)), "join");
        r.record(List.of(new PlayerSnapshot(A, "Oyuncu1", 100)), "quit");
        r.flush(online);
        List<String> k = keys(sent.get(0));
        assertEquals(2, k.size());
        assertNotEquals(k.get(0), k.get(1));
    }

    @Test
    void unackedSamplesAreResentAfterTimeoutOnly() {
        PlaytimeReporter r = reporter(null);
        r.record(List.of(new PlayerSnapshot(A, "Oyuncu1", 100)), "interval");
        r.flush(online);
        r.onAck(List.of(), online); // the backend knows player_stats but did not ACK this sample (transient error)
        assertEquals(1, sent.size(), "not resent immediately");
        clock.addAndGet(PlaytimeReporter.RESEND_AFTER_MS);
        assertEquals(1, r.flush(online));
        assertEquals(2, sent.size());
    }

    @Test
    void oldBackendWithoutAckGetsOnlyOneProbeBatchPerConnection() {
        PlaytimeReporter r = reporter(null);
        List<PlayerSnapshot> many = new ArrayList<>();
        for (int i = 0; i < PlaytimeReporter.BATCH_SIZE + 50; i++) {
            many.add(new PlayerSnapshot(new UUID(1, i), "p" + i, i));
        }
        r.record(many, "interval");
        assertEquals(PlaytimeReporter.BATCH_SIZE, r.flush(online), "probe batch only");
        clock.addAndGet(PlaytimeReporter.PROBE_TIMEOUT_MS + 1);
        assertEquals(0, r.flush(online), "no ack: backend does not support player_stats");
        r.onConnected();
        assertEquals(PlaytimeReporter.BATCH_SIZE, r.flush(online), "new connection probes again");
        r.onAck(keys(sent.get(1)), online);
        assertEquals(3, sent.size(), "after the first ack the rest is sent");
        assertEquals(50, keys(sent.get(2)).size());
        r.onAck(keys(sent.get(2)), online);
        assertEquals(0, r.queued());
    }

    @Test
    void importHistoryFlagIsSent() {
        PlaytimeReporter r = reporter(null);
        r.setImportHistory(true);
        r.record(List.of(new PlayerSnapshot(A, "Oyuncu1", 100)), "join");
        r.flush(online);
        assertTrue(sent.get(0).get("importHistory").getAsBoolean());
    }

    @Test
    void queueIsBoundedDroppingOldestSamples() {
        PlaytimeStore store = new PlaytimeStore(null, null);
        List<StatSample> batch = new ArrayList<>();
        for (int i = 0; i < PlaytimeStore.MAX_SAMPLES + 10; i++) {
            batch.add(new StatSample(new UUID(2, i).toString(), "p", i, 0, i, i + 1, "interval"));
        }
        store.add(batch);
        assertEquals(PlaytimeStore.MAX_SAMPLES, store.size());
        assertEquals(11, store.pending().get(0).sampledAt, "oldest dropped");
    }

    @Test
    void corruptStoreIsKeptAsideAndStartsEmpty() throws Exception {
        Path file = dir.resolve("playtime.json");
        Files.writeString(file, "{not json");
        PlaytimeStore store = new PlaytimeStore(file, null);
        assertEquals(0, store.size());
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("playtime.json.corrupt-")));
        }
    }

    @Test
    void snapshotBatchDeliversOnceWhenAllDoneOrSkipped() {
        List<List<PlayerSnapshot>> delivered = new ArrayList<>();
        SnapshotBatch b = new SnapshotBatch(3, delivered::add);
        b.add(new PlayerSnapshot(A, "Oyuncu1", 1));
        b.skip(); // player who left
        assertTrue(delivered.isEmpty());
        b.add(null); // okunamayan
        assertEquals(1, delivered.size());
        assertEquals(1, delivered.get(0).size());
        b.skip();
        assertEquals(1, delivered.size(), "delivered once");
        new SnapshotBatch(0, delivered::add);
        assertEquals(2, delivered.size(), "empty server delivers immediately");
    }
}
