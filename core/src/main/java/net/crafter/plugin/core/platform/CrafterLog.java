package net.crafter.plugin.core.platform;

/**
 * Platform-independent logging interface. Spigot and BungeeCord use java.util.logging, Velocity uses SLF4J.
 * Tokens, link codes and secret values are never logged at any level.
 */
public interface CrafterLog {
    void info(String message);
    void warn(String message);
    void error(String message);

    /** Written only when debug-mode is on */
    default void debug(String message) {}

    static CrafterLog ofJul(java.util.logging.Logger logger, java.util.function.BooleanSupplier debug) {
        return new CrafterLog() {
            @Override public void info(String m) { logger.info(m); }
            @Override public void warn(String m) { logger.warning(m); }
            @Override public void error(String m) { logger.severe(m); }
            @Override public void debug(String m) { if (debug.getAsBoolean()) logger.info("[debug] " + m); }
        };
    }

    /** Writes nothing (tests) */
    static CrafterLog silent() {
        return new CrafterLog() {
            @Override public void info(String m) {}
            @Override public void warn(String m) {}
            @Override public void error(String m) {}
        };
    }
}
