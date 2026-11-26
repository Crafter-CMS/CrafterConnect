package net.crafter.plugin.core.playtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AfkTrackerTest {

    static final UUID A = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final UUID B = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void countsOnlyWhileAfkFromSecondPoll() {
        AfkTracker t = new AfkTracker(30_000);
        AfkSource afkA = (uuid, now) -> uuid.equals(A);
        t.poll(List.of(A, B), afkA, 0);          // başlangıç
        t.poll(List.of(A, B), afkA, 30_000);
        t.poll(List.of(A, B), afkA, 60_000);
        assertEquals(60, t.totalSeconds(A));
        assertEquals(0, t.totalSeconds(B));
    }

    @Test
    void longGapIsCappedAndQuitRestartsCounting() {
        AfkTracker t = new AfkTracker(30_000);
        AfkSource always = (uuid, now) -> true;
        t.poll(List.of(A), always, 0);
        t.poll(List.of(A), always, 10 * 60_000); // sunucu dondu: en çok 2 × 30 sn
        assertEquals(60, t.totalSeconds(A));
        t.onQuit(A);
        t.poll(List.of(A), always, 20 * 60_000); // yeniden giriş: başlangıç, süre eklenmez
        assertEquals(60, t.totalSeconds(A));
        t.poll(List.of(A), always, 20 * 60_000 + 30_000);
        assertEquals(90, t.totalSeconds(A));
    }

    @Test
    void failingSourceCountsAsActive() {
        AfkTracker t = new AfkTracker(30_000);
        AfkSource broken = (uuid, now) -> { throw new IllegalStateException("plugin gone"); };
        t.poll(Set.of(A), broken, 0);
        t.poll(Set.of(A), broken, 30_000);
        assertEquals(0, t.totalSeconds(A));
    }

    @Test
    void exportLoadKeepsTotalsAndDropsStalePlayers() {
        AfkTracker t = new AfkTracker(30_000);
        t.poll(List.of(A, B), (u, n) -> true, 1_000);
        t.poll(List.of(A, B), (u, n) -> true, 31_000);
        Map<String, long[]> saved = t.export();

        AfkTracker reloaded = new AfkTracker(30_000);
        reloaded.load(saved, 0);
        assertEquals(30, reloaded.totalSeconds(A));

        AfkTracker pruned = new AfkTracker(30_000);
        pruned.load(saved, 40_000); // 40 sn'den önce görülenler eski
        assertEquals(0, pruned.totalSeconds(A));
    }

    @Test
    void idleSourceUsesThresholdAndActivity() {
        IdleAfkSource idle = new IdleAfkSource(5 * 60_000);
        assertFalse(idle.isAfk(A, 0), "first look starts the clock");
        assertFalse(idle.isAfk(A, 4 * 60_000));
        assertTrue(idle.isAfk(A, 5 * 60_000));
        idle.markActive(A, 5 * 60_000 + 1);
        assertFalse(idle.isAfk(A, 6 * 60_000));
        assertEquals(60_000, new IdleAfkSource(1).thresholdMs(), "minimum one minute");
    }

    @Test
    void idleTrackerCountsOnlyTimeAfterThreshold() {
        IdleAfkSource idle = new IdleAfkSource(60_000);
        AfkTracker t = new AfkTracker(30_000);
        idle.markActive(A, 0);
        for (long now = 0; now <= 180_000; now += 30_000) t.poll(List.of(A), idle, now);
        // 60 sn'de eşik aşıldı; 90, 120, 150, 180 yoklamaları (60 sn'deki dahil) AFK
        assertEquals(150, t.totalSeconds(A));
    }
}
