package net.crafter.plugin.core.platform;

/**
 * Platformdan bağımsız log arayüzü. Spigot ve BungeeCord'da java.util.logging, Velocity'de SLF4J kullanılır.
 * Token, bağlantı kodu ve gizli değerler hiçbir seviyede loglanmaz.
 */
public interface CrafterLog {
    void info(String message);
    void warn(String message);
    void error(String message);

    /** debug-mode açıksa yazılır */
    default void debug(String message) {}

    static CrafterLog ofJul(java.util.logging.Logger logger, java.util.function.BooleanSupplier debug) {
        return new CrafterLog() {
            @Override public void info(String m) { logger.info(m); }
            @Override public void warn(String m) { logger.warning(m); }
            @Override public void error(String m) { logger.severe(m); }
            @Override public void debug(String m) { if (debug.getAsBoolean()) logger.info("[debug] " + m); }
        };
    }

    /** Hiçbir şey yazmaz (testler) */
    static CrafterLog silent() {
        return new CrafterLog() {
            @Override public void info(String m) {}
            @Override public void warn(String m) {}
            @Override public void error(String m) {}
        };
    }
}
