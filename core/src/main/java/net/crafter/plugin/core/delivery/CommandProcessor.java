package net.crafter.plugin.core.delivery;

import net.crafter.plugin.core.CommandExecutor;
import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.platform.CrafterLog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Delivery rules (#37 section 3.4), transport-independent:
 * <ol>
 *   <li>If the same commandId arrives twice it is not run; the previous result (executed/queued) is reported again.</li>
 *   <li>A command that fails the security check is not run and a NACK is sent.</li>
 *   <li>If the player is offline the command goes to the persistent queue and {@code ack{status:"queued"}} is sent. When the player
 *   joins it runs and {@code ack{status:"executed"}} is sent.</li>
 *   <li>The command runs on the main thread; the result is written to the outbox as an ACK or NACK and sent right away
 *   if connected.</li>
 * </ol>
 * The command text and player name are never logged; only the commandId is written.
 */
public final class CommandProcessor {

    public static final String REASON_EXECUTION_FAILED = "execution_failed";

    /** Writes the ACK/NACK to the connection; false if not connected */
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

    /** Command received from the backend */
    public void onCommand(PendingCommand pc) {
        if (pc == null || pc.getCommand() == null) return;
        String id = pc.getCommandId();

        if (id != null) {
            // The backend resends with the same id when it gets no ACK
            if (store.isRunning(id)) return; // the result will be sent shortly
            if (store.isProcessed(id)) {
                // The previous result is repeated as is: a rejected command gets a NACK again
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
                // A rejected id also counts as processed: if the same command arrives again it still does not run
                if (store.beginRun(id)) store.finishRun(id, CommandStore.Outgoing.nack(id, reason));
                flush();
            }
            return;
        }

        if (pc.hasPlayer() && pc.getPlayer().contains(" ")
                && !executor.isPlayerOnline(pc.getPlayer()) && executor.isPlayerOnline(pc.getPlayer().replace(' ', '_'))) {
            // Floodgate turns spaces in the in-game name into "_"; %player% is filled with the in-game name (Bukkit does not
            // interpret quotes, so a spaced name would reach the command as two arguments)
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

    /** Player joined: runs the queued commands in order */
    public void onPlayerJoin(String playerName, UUID uuid) {
        List<PendingCommand> due = store.drainFor(playerName, uuid == null ? null : uuid.toString());
        for (PendingCommand pc : due) {
            // %player% is filled with the player's in-game name (name changes, Floodgate "_")
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
                // format already checked in CommandGuard
            }
        }
        String name = pc.getPlayer();
        if (executor.isPlayerOnline(name)) return true;
        // By default Floodgate turns spaces in the Bedrock name into "_"
        return name != null && name.contains(" ") && executor.isPlayerOnline(name.replace(' ', '_'));
    }

    private void reply(CommandStore.Outgoing outgoing) {
        if (outgoing.commandId == null) return;
        // Without a connection it goes to the outbox and is sent once connected
        Sender s = sender;
        if (s == null || !s.send(outgoing)) store.addOutgoing(outgoing);
    }

    /** Sends the ACK/NACKs in the outbox (once connected and after every result) */
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
