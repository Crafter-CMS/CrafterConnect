package net.crafter.plugin.core.connect;

/**
 * Connection error. {@link #code()} is the backend error type (link_code_invalid, link_code_expired, plan_limit_reached,
 * server_not_found, game_not_supported, plugin_protocol_unsupported) or a local code (invalid_code_format,
 * rate_limited, network_error, invalid_response, server_error).
 */
public class ConnectException extends Exception {
    private final String code;
    private final String detail;

    public ConnectException(String code, String detail) {
        super(code);
        this.code = code;
        this.detail = detail;
    }

    public String code() { return code; }

    /** Minimum version for plugin_protocol_unsupported, null otherwise */
    public String detail() { return detail; }
}
