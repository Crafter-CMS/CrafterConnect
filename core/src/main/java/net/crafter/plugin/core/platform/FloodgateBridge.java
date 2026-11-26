package net.crafter.plugin.core.platform;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Geyser/Floodgate integration (optional, via reflection; does nothing without Floodgate).
 * The Floodgate API is the same on Paper/Spigot, Velocity and BungeeCord:
 * {@code FloodgateApi.getInstance().getPlayerPrefix()} and {@code isFloodgatePlayer(UUID)}.
 */
public final class FloodgateBridge {

    private final Object api;
    private final Method prefixMethod;
    private final Method isFloodgateMethod;

    private FloodgateBridge(Object api, Method prefixMethod, Method isFloodgateMethod) {
        this.api = api;
        this.prefixMethod = prefixMethod;
        this.isFloodgateMethod = isFloodgateMethod;
    }

    /** Null if Floodgate is not installed or the API changed */
    public static FloodgateBridge detect(ClassLoader loader) {
        try {
            Class<?> cls = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, loader);
            Object api = cls.getMethod("getInstance").invoke(null);
            if (api == null) return null;
            return new FloodgateBridge(api, cls.getMethod("getPlayerPrefix"), cls.getMethod("isFloodgatePlayer", UUID.class));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Floodgate's Bedrock player name prefix (default "."); null if it cannot be read */
    public String playerPrefix() {
        try {
            Object v = prefixMethod.invoke(api);
            return v == null ? null : v.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean isBedrock(UUID uuid) {
        if (uuid == null) return false;
        try {
            return Boolean.TRUE.equals(isFloodgateMethod.invoke(api, uuid));
        } catch (Throwable t) {
            return false;
        }
    }
}
