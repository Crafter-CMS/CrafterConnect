package net.crafter.plugin.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CrafterConfigTest {

    @Test
    void testDefaultConfigIsNotConfigured() {
        CrafterConfig config = new CrafterConfig();
        assertFalse(config.isConfigured(), "Default config should not be considered configured");
    }

    @Test
    void testPartialDefaultConfigIsNotConfigured() {
        CrafterConfig config = new CrafterConfig();
        config.setWebsiteId("my-valid-website-id");
        // pluginSecret and serverId are still default
        assertFalse(config.isConfigured());

        config.setPluginSecret("my-secret-token");
        // serverId is still default
        assertFalse(config.isConfigured());

        // legacy default check
        config.setServerId("my-server-1");
        assertFalse(config.isConfigured());

        config.setServerId("my-proxy-1");
        assertFalse(config.isConfigured());
    }

    @Test
    void testFullyConfiguredIsValid() {
        CrafterConfig config = new CrafterConfig();
        config.setWebsiteId("web_12345");
        config.setPluginSecret("sec_abc123xyz");
        config.setServerId("survival-1");

        assertTrue(config.isConfigured(), "Config should be valid when all fields are properly provided");
    }

    @Test
    void testEmptyOrBlankIsNotConfigured() {
        CrafterConfig config = new CrafterConfig();
        config.setWebsiteId("web_12345");
        config.setPluginSecret("   ");
        config.setServerId("survival-1");

        assertFalse(config.isConfigured());
    }

    @Test
    void testYourPrefixIsNotConfigured() {
        CrafterConfig config = new CrafterConfig();
        config.setWebsiteId("YOUR_WEBSITE_ID");
        config.setPluginSecret("YOUR_PLUGIN_SECRET");
        config.setServerId("YOUR_SERVER_ID");

        assertFalse(config.isConfigured());

        config.setWebsiteId("your_website_custom");
        assertFalse(config.isConfigured());
    }

    @Test
    void tokenTakesPrecedenceOverLegacyFields() {
        CrafterConfig config = new CrafterConfig();
        config.setWebsiteId("web_12345");
        config.setPluginSecret("sec_abc123xyz");
        config.setServerId("survival-1");
        assertEquals(CrafterConfig.Mode.LEGACY, config.mode());
        config.setServerToken("cct_" + "a".repeat(43));
        assertEquals(CrafterConfig.Mode.TOKEN, config.mode());
        config.setServerToken("not-a-token");
        assertEquals(CrafterConfig.Mode.LEGACY, config.mode());
        assertFalse(config.toString().contains("sec_abc123xyz"));
    }

    @Test
    void apiUrlAcceptsHostOrUrl() {
        CrafterConfig c = new CrafterConfig();
        assertEquals("https://api.crafter.net.tr", c.httpBase());
        assertEquals("wss://api.crafter.net.tr", c.wsBase());
        c.setApiUrl("http://localhost:3000/");
        assertEquals("http://localhost:3000", c.httpBase());
        assertEquals("ws://localhost:3000", c.wsBase());
        c.setApiUrl("dev-api.crafter.net.tr");
        c.setUseSsl(false);
        assertEquals("http://dev-api.crafter.net.tr", c.httpBase());
    }

    @Test
    void readsV2AndOldKeys() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("server-token", "cct_" + "b".repeat(43));
        m.put("debug", "true"); // 1.x Velocity key
        m.put("language", "../../etc");
        m.put("report-players", "false");
        m.put("allowed-command-prefixes", java.util.List.of("lp user"));
        CrafterConfig c = CrafterConfig.from(net.crafter.plugin.core.support.Fakes.mapSource(m));
        assertEquals(CrafterConfig.Mode.TOKEN, c.mode());
        assertTrue(c.isDebug());
        assertEquals("tr", c.getLanguage(), "path characters are not allowed in language");
        assertFalse(c.isReportPlayers());
        assertEquals(java.util.List.of("lp user"), c.getAllowedCommandPrefixes());
    }

    @org.junit.jupiter.api.Test
    void playtimeSettingsAreClamped() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("playtime.sample-minutes", "0");
        m.put("playtime.afk-minutes", "999");
        m.put("playtime.afk-source", "../evil");
        CrafterConfig c = CrafterConfig.from(net.crafter.plugin.core.support.Fakes.mapSource(m));
        org.junit.jupiter.api.Assertions.assertTrue(c.isPlaytimeEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(1, c.getPlaytimeSampleMinutes());
        org.junit.jupiter.api.Assertions.assertEquals(120, c.getAfkMinutes());
        org.junit.jupiter.api.Assertions.assertEquals("auto", c.getAfkSource());
        org.junit.jupiter.api.Assertions.assertFalse(c.isPlaytimeImportHistory());
        org.junit.jupiter.api.Assertions.assertEquals(5, new CrafterConfig().getPlaytimeSampleMinutes());
    }
}
