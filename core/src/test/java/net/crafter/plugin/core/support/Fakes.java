package net.crafter.plugin.core.support;

import net.crafter.plugin.core.config.ConfigSource;
import net.crafter.plugin.core.connect.HttpTransport;
import net.crafter.plugin.core.platform.CrafterLog;
import net.crafter.plugin.core.platform.Platform;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Testlerde sahte platform, HTTP ve log (gerçek sunucu ya da ağ yok) */
public final class Fakes {
    private Fakes() {}

    public static final class CapturingLog implements CrafterLog {
        public final List<String> lines = Collections.synchronizedList(new ArrayList<>());
        @Override public void info(String m) { lines.add("INFO " + m); }
        @Override public void warn(String m) { lines.add("WARN " + m); }
        @Override public void error(String m) { lines.add("ERROR " + m); }
        @Override public void debug(String m) { lines.add("DEBUG " + m); }
        public String all() { synchronized (lines) { return String.join("\n", lines); } }
    }

    public static final class FakeHttp implements HttpTransport {
        public final List<String> methods = new ArrayList<>();
        public final List<URI> uris = new ArrayList<>();
        public final List<String> bodies = new ArrayList<>();
        public Function<URI, Response> responder = uri -> new Response(500, "");
        @Override public synchronized Response send(String method, URI uri, Map<String, String> headers, String body) {
            methods.add(method);
            uris.add(uri);
            bodies.add(body);
            return responder.apply(uri);
        }
    }

    public static ConfigSource mapSource(Map<String, Object> map) {
        return new ConfigSource() {
            @Override public boolean has(String key) { return map.containsKey(key); }
            @Override public String getString(String key, String def) { Object v = map.get(key); return v == null ? def : v.toString(); }
            @Override public int getInt(String key, int def) { Object v = map.get(key); return v == null ? def : Integer.parseInt(v.toString()); }
            @Override public boolean getBoolean(String key, boolean def) { Object v = map.get(key); return v == null ? def : Boolean.parseBoolean(v.toString()); }
            @SuppressWarnings("unchecked")
            @Override public List<String> getStringList(String key) { Object v = map.get(key); return v == null ? new ArrayList<>() : (List<String>) v; }
        };
    }

    /** Çevrimiçi oyuncu listesi ve çalıştırılan komutları tutan platform */
    public static class FakePlatform implements Platform {
        public final Set<String> online = Collections.synchronizedSet(new HashSet<>());
        public final Set<UUID> onlineUuids = Collections.synchronizedSet(new HashSet<>());
        public final List<String> executed = Collections.synchronizedList(new ArrayList<>());
        public final List<Runnable> deferred = Collections.synchronizedList(new ArrayList<>());
        /** true: komutlar hemen değil runDeferred() ile çalışır (ana thread benzetimi) */
        public volatile boolean defer = false;
        public volatile boolean result = true;
        public final CapturingLog log = new CapturingLog();
        public Path dir;
        public Map<String, Object> configValues = new HashMap<>();

        @Override public void dispatchConsole(String command, Consumer<Boolean> callback) {
            Runnable r = () -> { executed.add(command); callback.accept(result); };
            if (defer) deferred.add(r); else r.run();
        }
        public void runDeferred() {
            List<Runnable> copy;
            synchronized (deferred) { copy = new ArrayList<>(deferred); deferred.clear(); }
            copy.forEach(Runnable::run);
        }
        @Override public boolean isPlayerOnline(String playerName) {
            return playerName != null && online.stream().anyMatch(p -> p.equalsIgnoreCase(playerName));
        }
        @Override public boolean isPlayerOnline(UUID uuid) { return onlineUuids.contains(uuid); }
        @Override public void sendMessage(String username, String message) {}
        @Override public void handlePurchaseResponse(String username, boolean success, String message) {}
        @Override public String getPlatformName() { return "fake"; }
        @Override public String platformId() { return "paper"; }
        @Override public String gameVersion() { return "1.21.4"; }
        @Override public int onlinePlayers() { return online.size(); }
        @Override public int maxPlayers() { return 100; }
        @Override public Path dataDirectory() { return dir; }
        @Override public ConfigSource loadConfig() throws java.io.IOException {
            Path cfg = configFile();
            Map<String, Object> values = new HashMap<>(configValues);
            if (java.nio.file.Files.exists(cfg)) {
                // Basit "anahtar: "değer"" okuyucu (yalnızca üst düzey dizeler)
                for (String line : java.nio.file.Files.readAllLines(cfg)) {
                    var m = java.util.regex.Pattern.compile("^([a-z-]+):\\s*\"(.*)\"\\s*$").matcher(line);
                    if (m.matches()) values.put(m.group(1), m.group(2));
                }
            }
            return mapSource(values);
        }
        @Override public Map<String, String> loadMessages(String language) {
            return messages();
        }
        @Override public CrafterLog log() { return log; }
    }

    /** Paketteki tr.yml'dan basit okuma (testte YAML kütüphanesi yok) */
    public static Map<String, String> messages() {
        Map<String, String> out = new HashMap<>();
        try (var in = Fakes.class.getResourceAsStream("/lang/en.yml")) {
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var m = java.util.regex.Pattern.compile("(?m)^  ([a-z_]+): \"(.*)\"\\s*$").matcher(text);
            while (m.find()) out.put(m.group(1), m.group(2));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out;
    }
}
