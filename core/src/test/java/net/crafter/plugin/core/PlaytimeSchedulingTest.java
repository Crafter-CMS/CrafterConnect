package net.crafter.plugin.core;

import net.crafter.plugin.core.playtime.AfkSource;
import net.crafter.plugin.core.playtime.PlayerSnapshot;
import net.crafter.plugin.core.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Playtime scheduler (#529): with a shortened "minute", no real server or backend */
class PlaytimeSchedulingTest {

    static final UUID A = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir Path dir;
    CrafterConnectService service;

    /** Paper-like: stats are readable, the player is always AFK */
    static class StatsPlatform extends Fakes.FakePlatform {
        final AtomicInteger collects = new AtomicInteger();
        final AtomicInteger afkChecks = new AtomicInteger();
        volatile long play = 1000;
        @Override public boolean supportsPlayerStats() { return true; }
        @Override public void collectPlayerStats(Consumer<List<PlayerSnapshot>> done) {
            collects.incrementAndGet();
            play += 60;
            done.accept(List.of(new PlayerSnapshot(A, "Oyuncu1", play)));
        }
        @Override public Collection<UUID> onlinePlayerIds() { return List.of(A); }
        @Override public AfkSource afkSource(String mode, long idleThresholdMs) {
            return (u, n) -> { afkChecks.incrementAndGet(); return true; };
        }
    }

    CrafterConnectService start(Fakes.FakePlatform platform) throws Exception {
        platform.dir = dir;
        Files.writeString(dir.resolve("config.yml"), "api-url: \"http://127.0.0.1:9\"\nlanguage: \"en\"\n");
        CrafterConnectService s = new CrafterConnectService(platform, new Fakes.FakeHttp());
        s.playtimeMinuteMs = 40;
        s.afkPollMs = 15;
        s.start();
        return s;
    }

    @AfterEach
    void tearDown() {
        if (service != null) service.stop();
    }

    private static void waitFor(java.util.function.BooleanSupplier cond) throws InterruptedException {
        long end = System.currentTimeMillis() + 5_000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(10);
        assertTrue(cond.getAsBoolean());
    }

    @Test
    void samplesPeriodicallyPollsAfkAndQueuesWhileDisconnected() throws Exception {
        StatsPlatform platform = new StatsPlatform();
        service = start(platform);
        waitFor(() -> platform.collects.get() >= 3);
        assertTrue(platform.afkChecks.get() > 0);
        assertTrue(service.playtime().queued() >= 3, "not connected: samples stay queued");
        assertTrue(service.playtime().afkSeconds(A) >= 0);
        service.stop();
        int after = platform.collects.get();
        Thread.sleep(150);
        assertEquals(after, platform.collects.get(), "stop cancels the timer");
        assertTrue(Files.exists(dir.resolve("data/playtime.json")));
    }

    @Test
    void joinQuitAndShutdownSamplesAreRecorded() throws Exception {
        StatsPlatform platform = new StatsPlatform();
        platform.configValues.put("playtime.sample-minutes", "60"); // the scheduler takes no sample in this test
        service = start(platform);
        service.recordPlayerStats(new PlayerSnapshot(A, "Oyuncu1", 500), "join");
        service.recordPlayerStats(new PlayerSnapshot(A, "Oyuncu1", 800), "quit");
        waitFor(() -> service.playtime().queued() == 2);
        service.recordShutdown(List.of(new PlayerSnapshot(A, "Oyuncu1", 900)));
        assertEquals(3, service.playtime().queued());
    }

    @Test
    void disabledInConfigOrOnProxyDoesNothing() throws Exception {
        StatsPlatform platform = new StatsPlatform();
        platform.configValues.put("playtime.enabled", "false");
        service = start(platform);
        Thread.sleep(150);
        assertEquals(0, platform.collects.get());
        assertNull(service.playtime());
        service.stop();

        Fakes.FakePlatform proxy = new Fakes.FakePlatform(); // Velocity/BungeeCord: no stats
        service = start(proxy);
        assertNull(service.playtime());
        service.recordPlayerStats(new PlayerSnapshot(A, "Oyuncu1", 1), "join");
    }

    @Test
    void reloadAppliesNewInterval() throws Exception {
        StatsPlatform platform = new StatsPlatform();
        platform.configValues.put("playtime.sample-minutes", "60");
        service = start(platform);
        Thread.sleep(150);
        assertEquals(0, platform.collects.get());
        platform.configValues.put("playtime.sample-minutes", "1");
        service.reload();
        waitFor(() -> platform.collects.get() >= 2);
    }
}
