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
}
