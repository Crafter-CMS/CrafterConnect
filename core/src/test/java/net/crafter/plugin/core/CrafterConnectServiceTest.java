package net.crafter.plugin.core;

import net.crafter.plugin.core.connect.HttpTransport;
import net.crafter.plugin.core.support.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CrafterConnectServiceTest {

    static final String TOKEN = "cct_" + "Zz9_".repeat(11);

    @TempDir Path dir;
    private Fakes.FakePlatform platform;
    private Fakes.FakeHttp http;
    private CrafterConnectService service;

    @BeforeEach
    void setUp() throws Exception {
        platform = new Fakes.FakePlatform();
        platform.dir = dir;
        // Gerçek sunucuya bağlanılmaması için kapalı yerel port
        Files.writeString(dir.resolve("config.yml"), "# CrafterConnect\napi-url: \"http://127.0.0.1:9\"\nlanguage: \"en\"\n");
        platform.configValues.put("max-reconnect-attempts", "1");
        http = new Fakes.FakeHttp();
        service = new CrafterConnectService(platform, http);
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    private List<String> connect(String code) throws InterruptedException {
        List<String> replies = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(2); // "kontrol ediliyor" + sonuç
        service.connect(code, m -> { replies.add(m); done.countDown(); });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return replies;
    }

    @Test
    void notConnectedWarnsWithSetupHint() {
        assertTrue(platform.log.all().contains("/crafter connect"));
        assertEquals(net.crafter.plugin.core.config.CrafterConfig.Mode.NONE, service.config().mode());
    }

    @Test
    void connectStoresTokenPrivatelyAndNeverLogsIt() throws Exception {
        http.responder = uri -> new HttpTransport.Response(200, "{\"websiteId\":\"w1\",\"serverId\":\"srv-1\",\"serverToken\":\"" + TOKEN
                + "\",\"identityType\":\"minecraft_java\",\"store\":{\"name\":\"Demo\"}}");
        List<String> replies = connect("K7QM-4XPD");

        assertTrue(replies.get(1).contains("Demo"), replies.toString());
        String cfg = Files.readString(dir.resolve("config.yml"));
        assertTrue(cfg.contains("server-token: \"" + TOKEN + "\""));
        assertTrue(cfg.startsWith("# CrafterConnect"), "comments are kept");
        if (Files.getFileAttributeView(dir.resolve("config.yml"), PosixFileAttributeView.class) != null) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("config.yml"))));
        }
        assertEquals(net.crafter.plugin.core.config.CrafterConfig.Mode.TOKEN, service.config().mode());
        assertFalse(platform.log.all().contains(TOKEN), "token must never be logged");
        assertFalse(String.join("\n", replies).contains(TOKEN), "token must never be shown");
        assertTrue(service.statusLines().stream().anyMatch(l -> l.contains("srv-1")));
        assertTrue(service.statusLines().stream().anyMatch(l -> l.contains("Demo")));
        assertFalse(String.join("\n", service.statusLines()).contains(TOKEN));

        // /crafter disconnect token'ı siler
        List<String> out = new ArrayList<>();
        service.disconnect(out::add);
        assertTrue(Files.readString(dir.resolve("config.yml")).contains("server-token: \"\""));
        assertEquals(net.crafter.plugin.core.config.CrafterConfig.Mode.NONE, service.config().mode());
        assertFalse(Files.exists(dir.resolve("data/connection.json")));
    }

    @Test
    void connectErrorIsLocalizedAndTokenUntouched() throws Exception {
        http.responder = uri -> new HttpTransport.Response(410, "{\"type\":\"link_code_expired\"}");
        List<String> replies = connect("K7QM-4XPD");
        assertTrue(replies.get(1).contains("expired"), replies.toString());
        assertFalse(Files.readString(dir.resolve("config.yml")).contains("server-token"));
    }

    @Test
    void protocolErrorShowsMinimumVersion() throws Exception {
        http.responder = uri -> new HttpTransport.Response(426, "{\"type\":\"plugin_protocol_unsupported\",\"details\":{\"minVersion\":\"9.0.0\"}}");
        assertTrue(connect("K7QM-4XPD").get(1).contains("9.0.0"));
    }

    @Test
    void queuedCommandsRunOnJoinEvenWithoutConnection() {
        service.processor().onCommand(new net.crafter.plugin.core.model.PendingCommand("cmd_1", "give %player% x", "Notch"));
        assertTrue(platform.executed.isEmpty());
        service.onPlayerJoin("notch", null);
        assertEquals(List.of("give notch x"), platform.executed);
        assertTrue(Files.exists(dir.resolve("data/commands.json")));
    }

    @Test
    void adminCommandRequiresPermission() {
        AdminCommand admin = new AdminCommand(service);
        List<String> out = new ArrayList<>();
        AdminCommand.Sender noPerm = new AdminCommand.Sender() {
            @Override public boolean hasPermission(String p) { return false; }
            @Override public void send(String m) { out.add(m); }
        };
        assertTrue(admin.handle(noPerm, new String[]{"connect", "K7QM-4XPD"}));
        assertTrue(out.get(0).contains("permission"));
        assertTrue(http.uris.isEmpty());
        assertFalse(admin.handle(noPerm, new String[]{"shop"}), "shop is handled by the platform");
        assertEquals(List.of("connect"), AdminCommand.complete(new String[]{"co"}, true, List.of()));
        assertEquals(List.of(), AdminCommand.complete(new String[]{"co"}, false, List.of()));
    }
}
