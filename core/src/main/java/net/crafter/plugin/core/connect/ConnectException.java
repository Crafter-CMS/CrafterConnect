package net.crafter.plugin.core.connect;

/**
 * Bağlanma hatası. {@link #code()} backend hata tipidir (link_code_invalid, link_code_expired, plan_limit_reached,
 * server_not_found, game_not_supported, plugin_protocol_unsupported) ya da yerel bir kod (invalid_code_format,
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

    /** plugin_protocol_unsupported için asgari sürüm, diğerlerinde null */
    public String detail() { return detail; }
}
