package net.crafter.plugin.core.delivery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackoffTest {
    @Test
    void growsExponentiallyWithCapAndJitter() {
        Backoff b = new Backoff(5, 120);
        assertEquals(5, b.baseDelay(1));
        assertEquals(10, b.baseDelay(2));
        assertEquals(20, b.baseDelay(3));
        assertEquals(120, b.baseDelay(10));
        assertEquals(120, b.baseDelay(1000));
        for (int i = 1; i < 50; i++) {
            int d = b.delaySeconds(i);
            assertTrue(d >= 1 && d <= 120);
            assertTrue(d >= Math.floor(b.baseDelay(i) * 0.8) - 1);
        }
    }
}
