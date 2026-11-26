package net.crafter.plugin.core.model;

/**
 * Type carried by every WebSocket message.
 * Message types sent from the server (crafter.net.tr) to the plugin.
 */
public enum MessageType {
    // crafter.net.tr → Plugin
    AUTH_OK,          // Authentication succeeded
    AUTH_FAIL,        // Authentication failed
    COMMAND,          // Command to run
    STATISTICS,       // Statistics data
    PING,             // Liveness check

    // Plugin → crafter.net.tr
    AUTHENTICATE,     // Send the token on first connect
    ACK,              // Command ran successfully
    NACK,             // Command could not run (player offline, etc.)
    PONG              // Ping'e cevap
}
