package net.crafter.plugin.core.delivery;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Yeniden bağlanma bekleme süresi: üstel artış, üst sınır ve ±%20 rastgele sapma (çok sunucunun aynı anda
 * bağlanmasını önlemek için).
 */
public final class Backoff {

    private final int baseSeconds;
    private final int maxSeconds;

    public Backoff(int baseSeconds, int maxSeconds) {
        this.baseSeconds = Math.max(1, baseSeconds);
        this.maxSeconds = Math.max(this.baseSeconds, maxSeconds);
    }

    /** attempt 1'den başlar; sapmasız süre */
    public int baseDelay(int attempt) {
        int shift = Math.min(Math.max(attempt, 1) - 1, 16);
        long delay = (long) baseSeconds << shift;
        return (int) Math.min(delay, maxSeconds);
    }

    public int delaySeconds(int attempt) {
        int base = baseDelay(attempt);
        double jitter = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return (int) Math.max(1, Math.min(maxSeconds, Math.round(base * jitter)));
    }
}
