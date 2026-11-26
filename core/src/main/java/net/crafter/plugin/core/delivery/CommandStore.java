package net.crafter.plugin.core.delivery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.crafter.plugin.core.config.SecureFiles;
import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.platform.CrafterLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Teslimat komutlarının kalıcı deposu ({@code plugins/CrafterConnect/data/commands.json}, #371, #273).
 *
 * <ul>
 *   <li><b>Kuyruk:</b> oyuncu çevrimdışıyken gelen komutlar. Oyuncu adıyla (büyük/küçük harf duyarsız) ya da
 *   doğrulanmış UUID ile eşlenir; sunucu yeniden başlasa da kaybolmaz.</li>
 *   <li><b>İşlenen id'ler:</b> çalıştırılan ya da çalıştırılmaya başlanan komut id'leri en az 7 gün saklanır.
 *   Aynı commandId ikinci kez gelirse çalıştırılmaz, yalnızca ACK yinelenir.</li>
 *   <li><b>Giden kutusu:</b> bağlantı yokken üretilen ACK/NACK'ler; bağlantı kurulunca sırayla gönderilir.</li>
 * </ul>
 * Komut çalıştırılmadan hemen önce id "running" olarak diske yazılır (en çok bir kez çalıştırma). Sunucu bu arada
 * çökerse yeniden açılışta bu komut için NACK ({@code interrupted}) gönderilir ve sipariş incelemeye düşer; komut
 * tekrar çalıştırılmaz.
 */
public class CommandStore {

    static final long PROCESSED_RETENTION_MS = 7L * 24 * 60 * 60 * 1000;
    static final int MAX_PROCESSED = 50_000;
    public static final String REASON_INTERRUPTED = "interrupted";

    private final Path file;
    private final LongSupplier clock;
    private final CrafterLog log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final List<PendingCommand> queue = new ArrayList<>();
    private final LinkedHashMap<String, Processed> processed = new LinkedHashMap<>();
    private final List<Outgoing> outbox = new ArrayList<>();
    private final Set<String> inFlight = new HashSet<>();

    /** ACK ya da NACK */
    public static final class Outgoing {
        public final String commandId;
        public final String kind;   // "ack" | "nack"
        public final String status; // ack: executed | queued
        public final String reason; // nack nedeni

        public Outgoing(String commandId, String kind, String status, String reason) {
            this.commandId = commandId;
            this.kind = kind;
            this.status = status;
            this.reason = reason;
        }

        public static Outgoing ack(String id, String status) { return new Outgoing(id, "ack", status, null); }
        public static Outgoing nack(String id, String reason) { return new Outgoing(id, "nack", null, reason); }

        public boolean isAck() { return "ack".equals(kind); }

        @Override public boolean equals(Object o) {
            return o instanceof Outgoing other && Objects.equals(commandId, other.commandId)
                    && Objects.equals(kind, other.kind) && Objects.equals(status, other.status)
                    && Objects.equals(reason, other.reason);
        }
        @Override public int hashCode() { return Objects.hash(commandId, kind, status, reason); }
    }

    private static final class Processed {
        long at;
        String state; // running | done
        Processed() {}
        Processed(long at, String state) { this.at = at; this.state = state; }
    }

    private static final class Snapshot {
        int version = 2;
        List<PendingCommand> queue;
        Map<String, Processed> processed;
        List<Outgoing> outbox;
    }

    /**
     * @param file       depo dosyası; null ise yalnızca bellekte çalışır (testler)
     * @param legacyFile 2.0 öncesi geliştirme sürümündeki {@code pending-commands.json}; varsa taşınır ve silinir
     */
    public CommandStore(Path file, Path legacyFile, LongSupplier clock, CrafterLog log) {
        this.file = file;
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.log = log == null ? CrafterLog.silent() : log;
        load();
        migrateLegacy(legacyFile);
        recoverInterrupted();
        prune();
    }

    public CommandStore(Path file) {
        this(file, null, null, null);
    }

    /** Eşleme anahtarı: küçük harf; Floodgate oyun içinde boşlukları "_" yaptığı için boşluk "_" sayılır */
    public static String playerKey(String player) {
        return player == null ? "" : player.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    // --- Kuyruk ---

    public synchronized void enqueue(PendingCommand command) {
        command.setQueuedAt(clock.getAsLong());
        queue.add(command);
        save();
    }

    /** Oyuncunun bekleyen komutlarını geliş sırasıyla verir ve kuyruktan siler (ad ya da doğrulanmış UUID ile). */
    public synchronized List<PendingCommand> drainFor(String playerName, String uuid) {
        String key = playerKey(playerName);
        String uuidKey = normalizeUuid(uuid);
        List<PendingCommand> out = new ArrayList<>();
        Iterator<PendingCommand> it = queue.iterator();
        while (it.hasNext()) {
            PendingCommand pc = it.next();
            boolean byName = !key.isEmpty() && key.equals(playerKey(pc.getPlayer()));
            boolean byUuid = uuidKey != null && uuidKey.equals(normalizeUuid(pc.verifiedUuid()));
            if (byName || byUuid) {
                out.add(pc);
                it.remove();
            }
        }
        if (!out.isEmpty()) save();
        return out;
    }

    public synchronized boolean isQueued(String commandId) {
        if (commandId == null) return false;
        for (PendingCommand pc : queue) {
            if (commandId.equals(pc.getCommandId())) return true;
        }
        return false;
    }

    public synchronized int queuedCount() {
        return queue.size();
    }

    // --- İşlenen id'ler ---

    public synchronized boolean isProcessed(String commandId) {
        return commandId != null && (processed.containsKey(commandId) || inFlight.contains(commandId));
    }

    public synchronized boolean isRunning(String commandId) {
        return commandId != null && inFlight.contains(commandId);
    }

    /**
     * Komutu çalıştırmaya başlamadan önce çağrılır. Aynı id daha önce işlendiyse ya da şu an çalışıyorsa false.
     * id null ise (çok eski backend) her zaman true.
     */
    public synchronized boolean beginRun(String commandId) {
        if (commandId == null) return true;
        if (processed.containsKey(commandId) || inFlight.contains(commandId)) return false;
        inFlight.add(commandId);
        processed.put(commandId, new Processed(clock.getAsLong(), "running"));
        prune();
        save();
        return true;
    }

    /** Çalıştırma bitti; sonuç ACK/NACK'i aynı yazımda giden kutusuna eklenir. */
    public synchronized void finishRun(String commandId, Outgoing result) {
        if (commandId == null) return;
        inFlight.remove(commandId);
        processed.put(commandId, new Processed(clock.getAsLong(), "done"));
        if (result != null) outbox.add(result);
        save();
    }

    // --- Giden kutusu ---

    public synchronized void addOutgoing(Outgoing outgoing) {
        if (outgoing == null || outgoing.commandId == null) return;
        outbox.add(outgoing);
        save();
    }

    public synchronized List<Outgoing> outgoing() {
        return new ArrayList<>(outbox);
    }

    public synchronized void removeOutgoing(Collection<Outgoing> sent) {
        if (sent.isEmpty()) return;
        for (Outgoing o : sent) outbox.remove(o);
        save();
    }

    public synchronized int outgoingCount() {
        return outbox.size();
    }

    // --- Dosya ---

    private void prune() {
        long cutoff = clock.getAsLong() - PROCESSED_RETENTION_MS;
        processed.entrySet().removeIf(e -> "done".equals(e.getValue().state) && e.getValue().at < cutoff);
        Iterator<Map.Entry<String, Processed>> it = processed.entrySet().iterator();
        while (processed.size() > MAX_PROCESSED && it.hasNext()) {
            Map.Entry<String, Processed> e = it.next();
            if (!inFlight.contains(e.getKey())) it.remove();
        }
    }

    /** Önceki çalışmada "running" kalan komutlar: çalışıp çalışmadığı bilinmez, tekrar çalıştırılmaz, NACK edilir */
    private void recoverInterrupted() {
        boolean changed = false;
        for (Map.Entry<String, Processed> e : processed.entrySet()) {
            if ("running".equals(e.getValue().state)) {
                e.getValue().state = "done";
                outbox.add(Outgoing.nack(e.getKey(), REASON_INTERRUPTED));
                changed = true;
            }
        }
        if (changed) {
            log.warn("[CrafterConnect] Some commands were interrupted by a shutdown; they will be reported for review.");
            save();
        }
    }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Snapshot snapshot = gson.fromJson(json, Snapshot.class);
            if (snapshot == null) return;
            if (snapshot.queue != null) snapshot.queue.stream().filter(Objects::nonNull).forEach(queue::add);
            if (snapshot.processed != null) {
                snapshot.processed.forEach((id, p) -> { if (id != null && p != null) processed.put(id, p); });
            }
            if (snapshot.outbox != null) snapshot.outbox.stream().filter(o -> o != null && o.commandId != null).forEach(outbox::add);
        } catch (Exception e) {
            // Bozuk dosya silinmez; yanına kopyalanır ki elle incelenebilsin
            log.error("[CrafterConnect] Could not read command store: " + e.getMessage());
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt-" + clock.getAsLong()));
            } catch (IOException ignored) {
                // kopya alınamazsa devam edilir
            }
        }
    }

    /** TASK-273 biçimi: {"queue":{"oyuncu":[…]},"executedIds":[…]} */
    private void migrateLegacy(Path legacyFile) {
        if (legacyFile == null || !Files.exists(legacyFile)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(legacyFile, StandardCharsets.UTF_8)).getAsJsonObject();
            long now = clock.getAsLong();
            if (root.has("queue") && root.get("queue").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("queue").entrySet()) {
                    List<PendingCommand> list = gson.fromJson(e.getValue(), new TypeToken<List<PendingCommand>>() {}.getType());
                    if (list == null) continue;
                    for (PendingCommand pc : list) {
                        if (pc == null) continue;
                        if (pc.getPlayer() == null) pc.setPlayer(e.getKey());
                        if (pc.getCommandId() == null || !isQueued(pc.getCommandId())) queue.add(pc);
                    }
                }
            }
            if (root.has("executedIds") && root.get("executedIds").isJsonArray()) {
                for (JsonElement id : root.getAsJsonArray("executedIds")) {
                    if (id.isJsonPrimitive()) processed.putIfAbsent(id.getAsString(), new Processed(now, "done"));
                }
            }
            save();
            Files.deleteIfExists(legacyFile);
            log.info("[CrafterConnect] Pending command file moved to the data folder.");
        } catch (Exception e) {
            log.error("[CrafterConnect] Could not migrate old pending command file: " + e.getMessage());
        }
    }

    private void save() {
        if (file == null) return;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) SecureFiles.ensurePrivateDirectory(parent);
            Snapshot snapshot = new Snapshot();
            snapshot.queue = queue;
            snapshot.processed = processed;
            snapshot.outbox = outbox;
            SecureFiles.writeAtomically(file, gson.toJson(snapshot));
        } catch (IOException e) {
            log.error("[CrafterConnect] Could not write command store: " + e.getMessage());
        }
    }

    private static String normalizeUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;
        return uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }
}
