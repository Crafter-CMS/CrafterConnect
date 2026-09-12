package net.crafter.plugin.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateCheckerTest {

    @Test
    void testVersionComparison() {
        assertTrue(UpdateChecker.isNewerVersion("v1.0.5", "1.0.4"));
        assertTrue(UpdateChecker.isNewerVersion("1.0.5", "1.0.4"));
        assertTrue(UpdateChecker.isNewerVersion("1.1.0", "1.0.4"));
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.9.9"));

        assertFalse(UpdateChecker.isNewerVersion("v1.0.4", "1.0.4"));
        assertFalse(UpdateChecker.isNewerVersion("1.0.4", "1.0.4"));
        assertFalse(UpdateChecker.isNewerVersion("1.0.3", "1.0.4"));
        assertFalse(UpdateChecker.isNewerVersion("v1.0.3", "1.0.4"));
    }

    @Test
    void testSameVersionIsNotExpired() {
        JsonObject json = new JsonObject();
        json.addProperty("tag_name", "v1.0.4");
        json.addProperty("published_at", "2020-01-01T00:00:00Z");

        UpdateCheckResult result = UpdateChecker.parseReleaseJson(json, "1.0.4", Instant.now());
        assertFalse(result.hasUpdate());
        assertFalse(result.isExpired());
    }

    @Test
    void testNewerVersionWithinGracePeriodIsNotExpired() {
        Instant now = Instant.now();
        Instant published3DaysAgo = now.minus(3, ChronoUnit.DAYS);

        JsonObject json = new JsonObject();
        json.addProperty("tag_name", "v1.0.5");
        json.addProperty("published_at", published3DaysAgo.toString());
        json.addProperty("html_url", "https://github.com/Crafter-CMS/CrafterConnect/releases/tag/v1.0.5");

        UpdateCheckResult result = UpdateChecker.parseReleaseJson(json, "1.0.4", now);
        assertTrue(result.hasUpdate());
        assertEquals("v1.0.5", result.getLatestVersion());
        assertEquals(3, result.getDaysElapsed());
        assertFalse(result.isExpired(), "Should not be expired within 7 days grace period");
    }

    @Test
    void testNewerVersionAfterGracePeriodIsExpired() {
        Instant now = Instant.now();
        Instant published8DaysAgo = now.minus(8, ChronoUnit.DAYS);

        JsonObject json = new JsonObject();
        json.addProperty("tag_name", "v1.0.5");
        json.addProperty("published_at", published8DaysAgo.toString());
        json.addProperty("html_url", "https://github.com/Crafter-CMS/CrafterConnect/releases/tag/v1.0.5");

        UpdateCheckResult result = UpdateChecker.parseReleaseJson(json, "1.0.4", now);
        assertTrue(result.hasUpdate());
        assertEquals("v1.0.5", result.getLatestVersion());
        assertEquals(8, result.getDaysElapsed());
        assertTrue(result.isExpired(), "Should be expired after 7 days");
    }
}
