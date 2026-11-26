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
 * Persistent store for delivery commands ({@code plugins/CrafterConnect/data/commands.json}, #371, #273).
 *
 * <ul>
 *   <li><b>Queue:</b> commands received while the player is offline. Matched by player name (case-insensitive) or
 *   verified UUID; survives a server restart.</li>
 *   <li><b>Processed ids:</b> ids of commands that ran or started running are kept for at least 7 days.
 *   If the same commandId arrives again it is not run; only the ACK is repeated.</li>
 *   <li><b>Outbox:</b> ACK/NACKs produced while disconnected; sent in order once connected.</li>
 * </ul>
 * Right before a command runs, its id is written to disk as "running" (at-most-once execution). If the server
 * crashes in between, a NACK ({@code interrupted}) is sent for that command on the next startup and the order goes to review; the command
 * is not run again.
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

    /** ACK or NACK */
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
        /** Last result; absent in pre-2.0 records (then treated as "executed") */
        String kind;
        String status;
        String reason;
        Processed() {}
        Processed(long at, String state) { this.at = at; this.state = state; }
        Processed(long at, String state, Outgoing result) {
            this(at, state);
            if (result != null) {
                this.kind = result.kind;
                this.status = result.status;
                this.reason = result.reason;
            }
        }
    }

    private static final class Snapshot {
        int version = 2;
        List<PendingCommand> queue;
        Map<String, Processed> processed;
        List<Outgoing> outbox;
    }

    /**
     * @param file       store file; if null, works in memory only (tests)
     * @param legacyFile {@code pending-commands.json} from the pre-2.0 development build; migrated and deleted if present
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

    /** Lookup key: lower case; Floodgate turns spaces into "_" in game, so a space counts as "_" */
    public static String playerKey(String player) {
        return player == null ? "" : player.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    // --- Kuyruk ---

    public synchronized void enqueue(PendingCommand command) {
        command.setQueuedAt(clock.getAsLong());
        queue.add(command);
        save();
    }

    /** Returns the player's pending commands in arrival order and removes them from the queue (by name or verified UUID). */
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

    // --- Processed ids ---

    public synchronized boolean isProcessed(String commandId) {
        return commandId != null && (processed.containsKey(commandId) || inFlight.contains(commandId));
    }

    /**
     * Last result of a processed command (sent again when the same commandId arrives). Returns a NACK for a rejected command,
     * so the backend does not mark the record "executed" by mistake. ack executed for old records with no stored result.
     */
    public synchronized Outgoing resultOf(String commandId) {
        Processed p = commandId == null ? null : processed.get(commandId);
        if (p == null || p.kind == null) return Outgoing.ack(commandId, "executed");
        return new Outgoing(commandId, p.kind, p.status, p.reason);
    }

    public synchronized boolean isRunning(String commandId) {
        return commandId != null && inFlight.contains(commandId);
    }

    /**
     * Called before the command starts running. False if the same id was already processed or is running right now.
     * Always true if the id is null (very old backend).
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

    /** Execution finished; the ACK/NACK result is added to the outbox in the same write. */
    public synchronized void finishRun(String commandId, Outgoing result) {
        if (commandId == null) return;
        inFlight.remove(commandId);
        processed.put(commandId, new Processed(clock.getAsLong(), "done", result));
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

    // --- File ---

    private void prune() {
        long cutoff = clock.getAsLong() - PROCESSED_RETENTION_MS;
        processed.entrySet().removeIf(e -> "done".equals(e.getValue().state) && e.getValue().at < cutoff);
        Iterator<Map.Entry<String, Processed>> it = processed.entrySet().iterator();
        while (processed.size() > MAX_PROCESSED && it.hasNext()) {
            Map.Entry<String, Processed> e = it.next();
            if (!inFlight.contains(e.getKey())) it.remove();
        }
    }

    /** Commands left "running" by the previous run: unknown whether they ran, not re-run, NACKed */
    private void recoverInterrupted() {
        boolean changed = false;
        for (Map.Entry<String, Processed> e : processed.entrySet()) {
            if ("running".equals(e.getValue().state)) {
                Outgoing nack = Outgoing.nack(e.getKey(), REASON_INTERRUPTED);
                e.getValue().state = "done";
                e.getValue().kind = nack.kind;
                e.getValue().reason = nack.reason;
                outbox.add(nack);
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
            // A corrupt file is not deleted; it is copied aside so it can be inspected by hand
            log.error("[CrafterConnect] Could not read command store: " + e.getMessage());
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt-" + clock.getAsLong()));
            } catch (IOException ignored) {
                // continue if the copy fails
            }
        }
    }

    /** TASK-273 format: {"queue":{"player":[…]},"executedIds":[…]} */
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
