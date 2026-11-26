package net.crafter.plugin.core;

import java.util.function.Consumer;

/**
 * Platform-specific command execution and player operations.
 */
public interface CommandExecutor {

    /**
     * Runs the command as the console sender on the platform's main thread (on Folia the global region scheduler, on a
     * proxy a sequential executor). Does not block the calling thread; arrival order is preserved.
     *
     * @param command  validated command, without the leading "/"
     * @param callback result (true: the command ran); may be called from any thread
     */
    void dispatchConsole(String command, Consumer<Boolean> callback);

    /** Whether the player is online (name is case-insensitive) */
    boolean isPlayerOnline(String playerName);

    /** Whether the player with this UUID is online */
    default boolean isPlayerOnline(java.util.UUID uuid) {
        return false;
    }

    void sendMessage(String username, String message);

    void handlePurchaseResponse(String username, boolean success, String message);

    /** Platform name and version shown on the console */
    String getPlatformName();
}
