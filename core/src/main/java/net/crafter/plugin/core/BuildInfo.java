package net.crafter.plugin.core;

import java.io.InputStream;
import java.util.Properties;

/** Plugin version (written from the Maven version at build time) */
public final class BuildInfo {

    /** Protocol spoken with the backend (#37): dedupe by commandId, persistent queue, hello/heartbeat */
    public static final int PROTOCOL_VERSION = 2;
    /** Game type of this plugin (catalog id) */
    public static final String GAME = "minecraft";

    private static final String VERSION = loadVersion();

    private BuildInfo() {}

    public static String version() {
        return VERSION;
    }

    private static String loadVersion() {
        try (InputStream in = BuildInfo.class.getResourceAsStream("/crafter-connect-build.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("version", "").trim();
                if (!v.isEmpty() && !v.startsWith("$")) return v;
            }
        } catch (Exception ignored) {
            // if the version cannot be read the default below is used
        }
        return "2.0.0";
    }
}
