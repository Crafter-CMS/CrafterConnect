package net.crafter.plugin.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UpdateCheckerTest {

    @Test
    void testVersionComparison() {
        assertTrue(UpdateChecker.isNewerVersion("v1.0.5", "1.0.4"));
        assertTrue(UpdateChecker.isNewerVersion("1.1.0", "1.0.4"));
        assertTrue(UpdateChecker.isNewerVersion("2.0.0", "1.9.9"));
        assertFalse(UpdateChecker.isNewerVersion("v1.0.4", "1.0.4"));
        assertFalse(UpdateChecker.isNewerVersion("1.0.3", "1.0.4"));
    }

    @Test
    void newerReleaseIsOnlyANotice() {
        JsonObject json = new JsonObject();
        json.addProperty("tag_name", "v2.0.1");
        json.addProperty("html_url", "https://github.com/Crafter-CMS/CrafterConnect/releases/tag/v2.0.1");
        UpdateCheckResult result = UpdateChecker.parseReleaseJson(json, "2.0.0");
        assertTrue(result.hasUpdate());
        assertEquals("v2.0.1", result.getLatestVersion());
        assertFalse(UpdateChecker.parseReleaseJson(json, "2.0.1").hasUpdate());
    }

    @Test
    void catalogMinVersion() {
        assertTrue(UpdateChecker.isBelowMinimum("1.0.5", "2.0.0"));
        assertFalse(UpdateChecker.isBelowMinimum("2.0.0", "2.0.0"));
        assertFalse(UpdateChecker.isBelowMinimum("2.1.0", "2.0.0"));
        assertFalse(UpdateChecker.isBelowMinimum("2.0.0", null));
    }
}
