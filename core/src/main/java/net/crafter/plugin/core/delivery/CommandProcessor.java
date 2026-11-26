package net.crafter.plugin.core.delivery;

import net.crafter.plugin.core.CommandExecutor;
import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.platform.CrafterLog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Teslimat kuralları (#37 bölüm 3.4), taşımadan bağımsız:
 * <ol>
 *   <li>Aynı commandId ikinci kez gelirse çalıştırılmaz; önceki sonuç (executed/queued) yeniden bildirilir.</li>
 *   <li>Güvenlik kontrolünden geçmeyen komut çalıştırılmaz, NACK gönderilir.</li>
 *   <li>Oyuncu çevrimdışıysa komut kalıcı kuyruğa alınır ve {@code ack{status:"queued"}} gönderilir. Oyuncu girince
 *   çalıştırılır ve {@code ack{status:"executed"}} gönderilir.</li>
 *   <li>Komut ana thread'de çalışır; sonuç ACK ya da NACK olarak giden kutusuna yazılır ve bağlantı varsa hemen
 *   gönderilir.</li>
 * </ol>
 * Komut metni ve oyuncu adı loglanmaz; yalnızca commandId yazılır.
 */
public final class CommandProcessor {

    public static final String REASON_EXECUTION_FAILED = "execution_failed";

    /** ACK/NACK'i bağlantıya yazar; bağlantı yoksa false */
    public interface Sender {
        boolean send(CommandStore.Outgoing outgoing);
    }

    private final CommandStore store;
    private final CommandExecutor executor;
    private final Supplier<CommandGuard> guard;
    private final CrafterLog log;
    private volatile Sender sender;

    public CommandProcessor(CommandStore store, CommandExecutor executor, Supplier<CommandGuard> guard, CrafterLog log) {
        this.store = store;
        this.executor = executor;
        this.guard = guard;
        this.log = log == null ? CrafterLog.silent() : log;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    public CommandStore store() {
        return store;
    }

    /** Backend'den gelen komut */
    public void onCommand(PendingCommand pc) {
        if (pc == null || pc.getCommand() == null) return;
        String id = pc.getCommandId();

        if (id != null) {
            // Backend ACK alamayınca aynı id ile yeniden gönderir
            if (store.isRunning(id)) return; // sonuç birazdan gönderilecek
            if (store.isProcessed(id)) {
                // Önceki sonuç aynen yinelenir: reddedilen komut yine NACK alır
                reply(store.resultOf(id));
                return;
            }
            if (store.isQueued(id)) {
                reply(CommandStore.Outgoing.ack(id, "queued"));
                return;
            }
        }

        String reason = guard.get().check(pc);
        if (reason != null) {
            log.warn("[CrafterConnect] Command " + id + " rejected (" + reason + ")");
            if (id != null) {
                // Reddedilen id de işlenmiş sayılır: aynı komut tekrar gelirse yine çalıştırılmaz
                if (store.beginRun(id)) store.finishRun(id, CommandStore.Outgoing.nack(id, reason));
                flush();
            }
            return;
        }

        if (pc.hasPlayer() && pc.getPlayer().contains(" ")
                && !executor.isPlayerOnline(pc.getPlayer()) && executor.isPlayerOnline(pc.getPlayer().replace(' ', '_'))) {
            // Floodgate oyun içi adda boşlukları "_" yapar; %player% oyundaki adla doldurulur (Bukkit tırnakları
            // yorumlamaz, boşluklu ad komuta iki argüman olarak gider)
            pc.setPlayer(pc.getPlayer().trim().replace(' ', '_'));
        }

        if (pc.waitsForPlayer() && !isOnline(pc)) {
            store.enqueue(pc);
            log.info("[CrafterConnect] Player offline; command " + id + " queued.");
            if (id != null) reply(CommandStore.Outgoing.ack(id, "queued"));
            return;
        }

        run(pc);
    }

    /** Oyuncu girdi: kuyruktaki komutları sırayla çalıştırır */
    public void onPlayerJoin(String playerName, UUID uuid) {
        List<PendingCommand> due = store.drainFor(playerName, uuid == null ? null : uuid.toString());
        for (PendingCommand pc : due) {
            // %player% oyuncunun oyundaki adıyla doldurulur (ad değişikliği, Floodgate "_")
            if (pc.hasPlayer() && playerName != null && !playerName.isBlank()) pc.setPlayer(playerName);
            String reason = guard.get().check(pc);
            if (reason != null) {
                log.warn("[CrafterConnect] Queued command " + pc.getCommandId() + " rejected (" + reason + ")");
                if (pc.getCommandId() != null && store.beginRun(pc.getCommandId())) {
                    store.finishRun(pc.getCommandId(), CommandStore.Outgoing.nack(pc.getCommandId(), reason));
                }
                continue;
            }
            run(pc);
        }
        if (!due.isEmpty()) flush();
    }

    private void run(PendingCommand pc) {
        String id = pc.getCommandId();
        if (!store.beginRun(id)) {
            if (!store.isRunning(id)) reply(store.resultOf(id));
            return;
        }
        String resolved = pc.resolveCommand();
        log.info("[CrafterConnect] Executing command " + id);
        try {
            executor.dispatchConsole(resolved, ok -> complete(id, Boolean.TRUE.equals(ok)));
        } catch (RuntimeException e) {
            log.error("[CrafterConnect] Command " + id + " could not be scheduled: " + e.getMessage());
            complete(id, false);
        }
    }

    private void complete(String id, boolean ok) {
        if (id == null) return;
        store.finishRun(id, ok
                ? CommandStore.Outgoing.ack(id, "executed")
                : CommandStore.Outgoing.nack(id, REASON_EXECUTION_FAILED));
        if (!ok) log.warn("[CrafterConnect] Command " + id + " failed to execute.");
        flush();
    }

    private boolean isOnline(PendingCommand pc) {
        String uuid = pc.verifiedUuid();
        if (uuid != null) {
            try {
                if (executor.isPlayerOnline(parseUuid(uuid))) return true;
            } catch (IllegalArgumentException ignored) {
                // biçim CommandGuard'da kontrol edildi
            }
        }
        String name = pc.getPlayer();
        if (executor.isPlayerOnline(name)) return true;
        // Floodgate varsayılan olarak Bedrock adındaki boşlukları "_" yapar
        return name != null && name.contains(" ") && executor.isPlayerOnline(name.replace(' ', '_'));
    }

    private void reply(CommandStore.Outgoing outgoing) {
        if (outgoing.commandId == null) return;
        // Bağlantı yoksa giden kutusuna yazılır ve bağlanınca gönderilir
        Sender s = sender;
        if (s == null || !s.send(outgoing)) store.addOutgoing(outgoing);
    }

    /** Giden kutusundaki ACK/NACK'leri gönderir (bağlantı kurulunca ve her sonuçtan sonra) */
    public synchronized void flush() {
        Sender s = sender;
        if (s == null) return;
        List<CommandStore.Outgoing> sent = new ArrayList<>();
        for (CommandStore.Outgoing o : store.outgoing()) {
            if (!s.send(o)) break;
            sent.add(o);
        }
        store.removeOutgoing(sent);
    }

    static UUID parseUuid(String raw) {
        String v = raw.trim();
        if (v.length() == 32) {
            v = v.substring(0, 8) + "-" + v.substring(8, 12) + "-" + v.substring(12, 16) + "-"
                    + v.substring(16, 20) + "-" + v.substring(20);
        }
        return UUID.fromString(v);
    }
}
