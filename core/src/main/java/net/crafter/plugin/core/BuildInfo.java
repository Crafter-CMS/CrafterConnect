package net.crafter.plugin.core;

import java.io.InputStream;
import java.util.Properties;

/** Eklentinin sürümü (Maven sürümünden derleme sırasında yazılır) */
public final class BuildInfo {

    /** Backend ile konuşulan protokol (#37): commandId ile tekilleştirme, kalıcı kuyruk, hello/heartbeat */
    public static final int PROTOCOL_VERSION = 2;
    /** Bu eklentinin oyun türü (katalog id'si) */
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
            // sürüm okunamazsa aşağıdaki varsayılan kullanılır
        }
        return "2.0.0";
    }
}
