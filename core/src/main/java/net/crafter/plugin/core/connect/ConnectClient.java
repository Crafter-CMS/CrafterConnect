package net.crafter.plugin.core.connect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.crafter.plugin.core.BuildInfo;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * CrafterConnect v2 HTTP uçları (#369 4.3, 4.6):
 * <ul>
 *   <li>{@code POST /plugin/v2/connect}: bağlantı kodu → sunucu token'ı. Kimlik doğrulama koddur.</li>
 *   <li>{@code GET /website/{websiteId}/config/servers/games}: platformun asgari eklenti sürümü.</li>
 * </ul>
 * Yanıt gövdesi (token içerir) loglanmaz.
 */
public final class ConnectClient {

    private final String httpBase;
    private final HttpTransport http;

    public ConnectClient(String httpBase, HttpTransport http) {
        this.httpBase = httpBase.endsWith("/") ? httpBase.substring(0, httpBase.length() - 1) : httpBase;
        this.http = http;
    }

    public ConnectResult connect(String rawCode, ServerInfo info) throws ConnectException {
        String code = LinkCode.normalize(rawCode);
        if (code == null) throw new ConnectException("invalid_code_format", null);

        JsonObject body = new JsonObject();
        body.addProperty("code", LinkCode.format(code));
        body.addProperty("game", BuildInfo.GAME);
        body.addProperty("platform", info.platform());
        body.addProperty("pluginVersion", info.pluginVersion());
        body.addProperty("protocol", BuildInfo.PROTOCOL_VERSION);
        body.addProperty("transport", "ws");
        JsonObject server = new JsonObject();
        if (info.host() != null && !info.host().isBlank()) server.addProperty("host", info.host());
        if (info.port() > 0) server.addProperty("port", info.port());
        if (info.gameVersion() != null) server.addProperty("gameVersion", info.gameVersion());
        if (info.maxPlayers() > 0) server.addProperty("maxPlayers", info.maxPlayers());
        body.add("server", server);

        HttpTransport.Response res;
        try {
            res = http.send("POST", URI.create(httpBase + "/plugin/v2/connect"), Map.of(), body.toString());
        } catch (IOException e) {
            throw new ConnectException("network_error", null);
        }
        if (res.status() == 200 || res.status() == 201) {
            JsonObject json = parseObject(res.body());
            String token = str(json, "serverToken");
            String serverId = str(json, "serverId");
            if (json == null || token == null || !token.matches("^cct_[A-Za-z0-9_-]{20,100}$") || serverId == null) {
                throw new ConnectException("invalid_response", null);
            }
            JsonObject store = json.has("store") && json.get("store").isJsonObject() ? json.getAsJsonObject("store") : null;
            return new ConnectResult(str(json, "websiteId"), serverId, token, str(json, "identityType"), str(store, "name"));
        }
        throw errorOf(res);
    }

    /** Platformun katalogdaki asgari sürümü; okunamazsa null */
    public String fetchMinVersion(String websiteId, String platform) {
        if (websiteId == null || !websiteId.matches("^[A-Za-z0-9_-]{1,64}$")) return null;
        try {
            HttpTransport.Response res = http.send("GET",
                    URI.create(httpBase + "/website/" + websiteId + "/config/servers/games"), Map.of(), null);
            if (res.status() != 200) return null;
            JsonObject json = parseObject(res.body());
            if (json == null || !json.has("games") || !json.get("games").isJsonArray()) return null;
            for (JsonElement g : json.getAsJsonArray("games")) {
                if (!g.isJsonObject() || !BuildInfo.GAME.equals(str(g.getAsJsonObject(), "id"))) continue;
                JsonElement platforms = g.getAsJsonObject().get("platforms");
                if (platforms == null || !platforms.isJsonArray()) return null;
                for (JsonElement p : platforms.getAsJsonArray()) {
                    if (p.isJsonObject() && platform.equals(str(p.getAsJsonObject(), "id"))) {
                        return str(p.getAsJsonObject(), "minVersion");
                    }
                }
            }
        } catch (Exception ignored) {
            // ağ ya da biçim hatası eklentinin çalışmasını etkilemez
        }
        return null;
    }

    static ConnectException errorOf(HttpTransport.Response res) {
        JsonObject json = parseObject(res.body());
        String type = str(json, "type");
        String minVersion = null;
        if (json != null && json.has("details") && json.get("details").isJsonObject()) {
            minVersion = str(json.getAsJsonObject("details"), "minVersion");
        }
        if (type != null && type.matches("^[a-z_]{3,60}$")) return new ConnectException(type, minVersion);
        return switch (res.status()) {
            case 400 -> new ConnectException("link_code_invalid", null);
            case 403 -> new ConnectException("plan_limit_reached", null);
            case 404 -> new ConnectException("server_not_found", null);
            case 410 -> new ConnectException("link_code_expired", null);
            case 422 -> new ConnectException("game_not_supported", null);
            case 426 -> new ConnectException("plugin_protocol_unsupported", minVersion);
            case 429 -> new ConnectException("rate_limited", null);
            default -> new ConnectException("server_error", null);
        };
    }

    private static JsonObject parseObject(String body) {
        try {
            JsonElement el = JsonParser.parseString(body == null ? "" : body);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive()) return null;
        return obj.get(key).getAsString();
    }
}
