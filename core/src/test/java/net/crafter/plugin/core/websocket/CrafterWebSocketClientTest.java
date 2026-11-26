package net.crafter.plugin.core.websocket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.crafter.plugin.core.LanguageManager;
import net.crafter.plugin.core.MarketManager;
import net.crafter.plugin.core.PlayerManager;
import net.crafter.plugin.core.StatisticsManager;
import net.crafter.plugin.core.config.CrafterConfig;
import net.crafter.plugin.core.connect.ServerInfo;
import net.crafter.plugin.core.delivery.CommandGuard;
import net.crafter.plugin.core.delivery.CommandProcessor;
import net.crafter.plugin.core.delivery.CommandStore;
import net.crafter.plugin.core.support.Fakes;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Protokol testi: 127.0.0.1 üzerinde sahte bir Crafter WebSocket sunucusu (gerçek backend yok).
 */
class CrafterWebSocketClientTest {

    static final String TOKEN = "cct_" + "Q1w2".repeat(11);

    @Test
    void tokenGoesInHeaderNotInUrl() {
        CrafterConfig c = new CrafterConfig();
        c.setServerToken(TOKEN);
        assertEquals("wss://api.crafter.net.tr/website/plugin/wss?protocol=2", CrafterWebSocketClient.createURI(c).toString());
        assertEquals("Bearer " + TOKEN, CrafterWebSocketClient.createHeaders(c).get("Authorization"));

        CrafterConfig legacy = new CrafterConfig();
        legacy.setWebsiteId("w1");
        legacy.setPluginSecret("s e&c");
        legacy.setServerId("srv");
        String uri = CrafterWebSocketClient.createURI(legacy).toString();
        assertTrue(uri.contains("website-id=w1&plugin-secret=s+e%26c&server-id=srv"));
        assertNull(CrafterWebSocketClient.createHeaders(legacy).get("Authorization"));
    }

    /** Sahte sunucu: bağlanınca auth_success ve bir komut yollar, gelen olayları kaydeder */
    static final class FakeBackend extends WebSocketServer {
        final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        volatile String authorization;
        volatile String resource;
        volatile String firstMessage;

        FakeBackend() { super(new InetSocketAddress("127.0.0.1", 0)); setReuseAddr(true); }

        @Override public void onOpen(WebSocket conn, ClientHandshake hs) {
            authorization = hs.getFieldValue("Authorization");
            resource = hs.getResourceDescriptor();
            String msg = firstMessage != null ? firstMessage
                    : "{\"type\":\"auth_success\",\"message\":\"Connected successfully\",\"payload\":{\"websiteId\":\"w1\",\"serverId\":\"srv-1\","
                    + "\"protocol\":2,\"game\":\"minecraft\",\"identityType\":\"minecraft_java\",\"settings\":{\"allowedCommandPrefixes\":[\"say\"]}}}";
            conn.send(msg);
            if (firstMessage == null) {
                conn.send("{\"type\":\"command\",\"payload\":{\"commandId\":\"cmd_1\",\"command\":\"say hi\",\"player\":null}}");
                conn.send("{\"type\":\"command\",\"payload\":{\"commandId\":\"cmd_2\",\"command\":\"give Notch x\",\"player\":null}}");
                conn.send("{\"type\":\"command\",\"payload\":{\"commandId\":\"cmd_1\",\"command\":\"say hi\",\"player\":null}}");
            }
        }
        @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {}
        @Override public void onMessage(WebSocket conn, String message) { received.add(message); }
        @Override public void onError(WebSocket conn, Exception ex) {}
        @Override public void onStart() {}
    }

    private static String nextEvent(BlockingQueue<String> q, String event) throws InterruptedException {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            String m = q.poll(500, TimeUnit.MILLISECONDS);
            if (m != null && JsonParser.parseString(m).getAsJsonObject().get("event").getAsString().equals(event)) return m;
        }
        fail("no " + event + " event");
        return null;
    }

    private CrafterWebSocketClient client(FakeBackend server, Fakes.FakePlatform platform, CommandProcessor processor,
                                          CrafterWebSocketClient.Listener listener) throws Exception {
        CrafterConfig c = new CrafterConfig();
        c.setApiUrl("127.0.0.1:" + server.getPort());
        c.setUseSsl(false);
        c.setServerToken(TOKEN);
        c.setMaxReconnectAttempts(1);
        return new CrafterWebSocketClient(c, platform, processor, new StatisticsManager(), new PlayerManager(),
                new MarketManager(), () -> new LanguageManager("en"),
                () -> new ServerInfo("paper", "2.0.0", "1.21.4", 100, null, 0, "."), listener);
    }

    private static FakeBackend startServer() throws Exception {
        FakeBackend server = new FakeBackend();
        server.start();
        for (int i = 0; i < 50 && server.getPort() == 0; i++) Thread.sleep(50);
        return server;
    }

    @Test
    void v2HandshakeHelloAcksAndRemotePrefixes() throws Exception {
        FakeBackend server = startServer();
        Fakes.FakePlatform platform = new Fakes.FakePlatform();
        CompletableFuture<CrafterWebSocketClient.AuthInfo> auth = new CompletableFuture<>();
        CommandStore store = new CommandStore(null);
        CommandProcessor processor = new CommandProcessor(store, platform,
                () -> new CommandGuard(null, "minecraft_java", List.of(), auth.getNow(null) == null ? List.of() : auth.getNow(null).allowedCommandPrefixes()),
                platform.log);
        CrafterWebSocketClient client = client(server, platform, processor, new CrafterWebSocketClient.Listener() {
            @Override public void onAuthenticated(CrafterWebSocketClient.AuthInfo info) { auth.complete(info); }
            @Override public void onTokenRejected(String reason) {}
        });
        try {
            client.start();
            CrafterWebSocketClient.AuthInfo info = auth.get(10, TimeUnit.SECONDS);
            assertEquals("srv-1", info.serverId());
            assertEquals(List.of("say"), info.allowedCommandPrefixes());

            JsonObject hello = JsonParser.parseString(nextEvent(server.received, "hello")).getAsJsonObject().getAsJsonObject("data");
            assertEquals("paper", hello.get("platform").getAsString());
            assertEquals("2.0.0", hello.get("pluginVersion").getAsString());
            assertEquals("1.21.4", hello.get("gameVersion").getAsString());
            assertEquals(100, hello.get("maxPlayers").getAsInt());
            assertEquals(".", hello.get("floodgatePrefix").getAsString());
            // Sahte platform istatistik okumaz (proxy gibi): player_stats yeteneği bildirilmez (#529)
            assertEquals(0, hello.getAsJsonArray("capabilities").size());

            JsonObject ack1 = JsonParser.parseString(nextEvent(server.received, "ack")).getAsJsonObject().getAsJsonObject("data");
            assertEquals("cmd_1", ack1.get("commandId").getAsString());
            assertEquals("executed", ack1.get("status").getAsString());
            JsonObject nack = JsonParser.parseString(nextEvent(server.received, "nack")).getAsJsonObject().getAsJsonObject("data");
            assertEquals("cmd_2", nack.get("commandId").getAsString());
            assertEquals(CommandGuard.REASON_PREFIX_NOT_ALLOWED, nack.get("reason").getAsString());
            JsonObject ack2 = JsonParser.parseString(nextEvent(server.received, "ack")).getAsJsonObject().getAsJsonObject("data");
            assertEquals("cmd_1", ack2.get("commandId").getAsString(), "duplicate is acked again");
            assertEquals(List.of("say hi"), platform.executed, "duplicate commandId runs once");

            assertEquals("Bearer " + TOKEN, server.authorization);
            assertEquals("/website/plugin/wss?protocol=2", server.resource);
            assertFalse(platform.log.all().contains(TOKEN));
        } finally {
            client.shutdown();
            server.stop(1000);
        }
    }

    @Test
    void invalidTokenStopsReconnecting() throws Exception {
        FakeBackend server = startServer();
        server.firstMessage = "{\"type\":\"error\",\"code\":\"server_token_invalid\",\"message\":\"Server token is invalid\"}";
        Fakes.FakePlatform platform = new Fakes.FakePlatform();
        CompletableFuture<String> rejected = new CompletableFuture<>();
        CommandProcessor processor = new CommandProcessor(new CommandStore(null), platform,
                () -> new CommandGuard(null, List.of(), List.of()), platform.log);
        CrafterWebSocketClient client = client(server, platform, processor, new CrafterWebSocketClient.Listener() {
            @Override public void onAuthenticated(CrafterWebSocketClient.AuthInfo info) {}
            @Override public void onTokenRejected(String reason) { rejected.complete("x"); }
        });
        try {
            client.start();
            rejected.get(10, TimeUnit.SECONDS);
            assertTrue(client.isStopped());
            assertEquals("server_token_invalid", client.lastError());
        } finally {
            client.shutdown();
            server.stop(1000);
        }
    }

    @Test
    void tokenRevocationCloseCodes() {
        CrafterConfig c = new CrafterConfig();
        c.setServerToken(TOKEN);
        Fakes.FakePlatform platform = new Fakes.FakePlatform();
        CrafterWebSocketClient client = new CrafterWebSocketClient(c, platform,
                new CommandProcessor(new CommandStore(null), platform, () -> new CommandGuard(null, List.of(), List.of()), null),
                new StatisticsManager(), new PlayerManager(), new MarketManager(), () -> new LanguageManager("en"),
                () -> new ServerInfo("paper", "2.0.0", "1", 1, null, 0, null), null);
        assertTrue(client.isTokenRevocation(4001, "Reconnected with a new token"));
        assertTrue(client.isTokenRevocation(1008, "Invalid server token"));
        assertFalse(client.isTokenRevocation(1008, "License Error"));
        assertFalse(client.isTokenRevocation(1006, ""));
    }
}
