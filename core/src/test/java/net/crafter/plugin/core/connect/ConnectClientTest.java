package net.crafter.plugin.core.connect;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.crafter.plugin.core.support.Fakes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectClientTest {

    static final String TOKEN = "cct_" + "Ab12".repeat(11);
    private final ServerInfo info = new ServerInfo("paper", "2.0.0", "1.21.4", 100, "203.0.113.10", 25565, ".");

    @Test
    void linkCodeNormalizationMatchesBackend() {
        assertEquals("K7QM4XPD", LinkCode.normalize("k7qm-4xpd"));
        assertEquals("K7QM4XPD", LinkCode.normalize(" K7QM 4XPD "));
        assertEquals("10QM4XPD", LinkCode.normalize("loQM-4XPD")); // l → 1, o → 0
        assertNull(LinkCode.normalize("K7QM-4XP"));
        assertNull(LinkCode.normalize("K7QM-4XPU")); // U is not in the alphabet
        assertNull(LinkCode.normalize("K7QM;4XPD"));
        assertEquals("K7QM-4XPD", LinkCode.format("K7QM4XPD"));
    }

    @Test
    void successfulConnectSendsContractAndParsesToken() throws Exception {
        Fakes.FakeHttp http = new Fakes.FakeHttp();
        http.responder = uri -> new HttpTransport.Response(200, "{\"websiteId\":\"w1\",\"serverId\":\"s1\",\"serverToken\":\"" + TOKEN
                + "\",\"identityType\":\"minecraft_java\",\"wsUrl\":\"wss://x\",\"store\":{\"name\":\"Café Store\"}}");
        ConnectResult r = new ConnectClient("https://api.crafter.net.tr/", http).connect("k7qm 4xpd", info);

        assertEquals("POST", http.methods.get(0));
        assertEquals("https://api.crafter.net.tr/plugin/v2/connect", http.uris.get(0).toString());
        JsonObject body = JsonParser.parseString(http.bodies.get(0)).getAsJsonObject();
        assertEquals("K7QM-4XPD", body.get("code").getAsString());
        assertEquals("minecraft", body.get("game").getAsString());
        assertEquals("paper", body.get("platform").getAsString());
        assertEquals("2.0.0", body.get("pluginVersion").getAsString());
        assertEquals(2, body.get("protocol").getAsInt());
        assertEquals("ws", body.get("transport").getAsString());
        assertEquals("1.21.4", body.getAsJsonObject("server").get("gameVersion").getAsString());
        assertEquals(100, body.getAsJsonObject("server").get("maxPlayers").getAsInt());
        assertEquals(25565, body.getAsJsonObject("server").get("port").getAsInt());

        assertEquals("s1", r.serverId());
        assertEquals(TOKEN, r.serverToken());
        assertEquals("Café Store", r.storeName());
        assertFalse(r.toString().contains(TOKEN));
    }

    @Test
    void invalidFormatDoesNotCallNetwork() {
        Fakes.FakeHttp http = new Fakes.FakeHttp();
        ConnectException e = assertThrows(ConnectException.class, () -> new ConnectClient("https://x", http).connect("abc", info));
        assertEquals("invalid_code_format", e.code());
        assertTrue(http.uris.isEmpty());
    }

    @Test
    void mapsBackendErrors() {
        assertEquals("link_code_invalid", code(400, "{\"success\":false,\"message\":\"…\",\"type\":\"link_code_invalid\"}"));
        assertEquals("link_code_expired", code(410, "{\"type\":\"link_code_expired\"}"));
        assertEquals("plan_limit_reached", code(403, "{\"type\":\"plan_limit_reached\",\"details\":{\"limit\":1}}"));
        assertEquals("game_not_supported", code(422, ""));
        assertEquals("rate_limited", code(429, "{\"statusCode\":429,\"message\":\"ThrottlerException\"}"));
        assertEquals("server_error", code(502, "<html>"));
        ConnectException e = error(426, "{\"type\":\"plugin_protocol_unsupported\",\"details\":{\"minVersion\":\"2.1.0\"}}");
        assertEquals("plugin_protocol_unsupported", e.code());
        assertEquals("2.1.0", e.detail());
    }

    @Test
    void rejectsMalformedTokenInResponse() {
        Fakes.FakeHttp http = new Fakes.FakeHttp();
        http.responder = uri -> new HttpTransport.Response(200, "{\"serverId\":\"s1\",\"serverToken\":\"abc\"}");
        ConnectException e = assertThrows(ConnectException.class, () -> new ConnectClient("https://x", http).connect("K7QM-4XPD", info));
        assertEquals("invalid_response", e.code());
    }

    @Test
    void networkFailureIsReported() {
        HttpTransport broken = (m, u, h, b) -> { throw new java.io.IOException("refused"); };
        ConnectException e = assertThrows(ConnectException.class, () -> new ConnectClient("https://x", broken).connect("K7QM-4XPD", info));
        assertEquals("network_error", e.code());
    }

    @Test
    void readsPlatformMinVersionFromCatalog() {
        Fakes.FakeHttp http = new Fakes.FakeHttp();
        http.responder = uri -> new HttpTransport.Response(200, "{\"games\":[{\"id\":\"rust\",\"platforms\":[]},{\"id\":\"minecraft\",\"platforms\":["
                + "{\"id\":\"paper\",\"minVersion\":\"2.0.0\",\"transport\":\"ws\"},{\"id\":\"velocity\",\"minVersion\":\"2.1.0\"}]}]}");
        ConnectClient c = new ConnectClient("https://api", http);
        assertEquals("2.1.0", c.fetchMinVersion("w1", "velocity"));
        assertEquals("https://api/website/w1/config/servers/games", http.uris.get(0).toString());
        assertNull(c.fetchMinVersion("../x", "paper"));
    }

    private static String code(int status, String body) {
        return error(status, body).code();
    }

    private static ConnectException error(int status, String body) {
        return ConnectClient.errorOf(new HttpTransport.Response(status, body));
    }
}
