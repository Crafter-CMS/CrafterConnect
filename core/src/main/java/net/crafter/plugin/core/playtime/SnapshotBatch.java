package net.crafter.plugin.core.playtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Folia'da her oyuncunun istatistiği kendi bölgesinde okunur; bu sınıf hepsi gelince (ya da çıkan oyuncular
 * {@link #skip()} edilince) listeyi bir kez teslim eder. Thread güvenlidir.
 */
public final class SnapshotBatch {

    private final List<PlayerSnapshot> out = new ArrayList<>();
    private final AtomicInteger remaining;
    private final AtomicBoolean delivered = new AtomicBoolean(false);
    private final Consumer<List<PlayerSnapshot>> done;

    public SnapshotBatch(int expected, Consumer<List<PlayerSnapshot>> done) {
        this.remaining = new AtomicInteger(Math.max(0, expected));
        this.done = done;
        if (expected <= 0) deliver();
    }

    public void add(PlayerSnapshot snapshot) {
        if (snapshot != null) {
            synchronized (out) {
                out.add(snapshot);
            }
        }
        countDown();
    }

    /** Oyuncu çıktı ya da okunamadı */
    public void skip() {
        countDown();
    }

    private void countDown() {
        if (remaining.decrementAndGet() == 0) deliver();
    }

    private void deliver() {
        if (!delivered.compareAndSet(false, true)) return;
        List<PlayerSnapshot> copy;
        synchronized (out) {
            copy = new ArrayList<>(out);
        }
        done.accept(copy);
    }
}
