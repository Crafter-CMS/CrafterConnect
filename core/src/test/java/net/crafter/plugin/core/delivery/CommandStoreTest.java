package net.crafter.plugin.core.delivery;

import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.model.PlayerIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CommandStoreTest {

    @TempDir Path dir;

    @Test
    void queueSurvivesRestartAndMatchesNameCaseInsensitively() {
        Path file = dir.resolve("data/commands.json");
        CommandStore store = new CommandStore(file);
        store.enqueue(new PendingCommand("cmd_1", "give %player% diamond 1", "Notch"));
        store.enqueue(new PendingCommand("cmd_2", "give %player% gold 1", "Other"));

        CommandStore reopened = new CommandStore(file);
        assertTrue(reopened.isQueued("cmd_1"));
        assertEquals(2, reopened.queuedCount());
        List<PendingCommand> due = reopened.drainFor("NOTCH", null);
        assertEquals(1, due.size());
        assertEquals("cmd_1", due.get(0).getCommandId());
        assertEquals(1, new CommandStore(file).queuedCount());
    }

    @Test
    void drainsByVerifiedUuidAndFloodgateUnderscore() {
        CommandStore store = new CommandStore(null);
        PendingCommand byUuid = new PendingCommand("cmd_u", "lp user OldName parent add vip", "OldName");
        byUuid.setIdentity(new PlayerIdentity("minecraft_uuid", "069a79f444e94726a5befca90e38aaf5"));
        store.enqueue(byUuid);
        store.enqueue(new PendingCommand("cmd_b", "give %player% x", ".Cool Gamer"));

        assertEquals(1, store.drainFor("NewName", "069a79f4-44e9-4726-a5be-fca90e38aaf5").size());
        assertEquals(1, store.drainFor(".Cool_Gamer", null).size());
        assertEquals(0, store.queuedCount());
    }

    @Test
    void processedIdsPreventSecondRunAcrossRestart() {
        Path file = dir.resolve("commands.json");
        CommandStore store = new CommandStore(file);
        assertTrue(store.beginRun("cmd_1"));
        assertFalse(store.beginRun("cmd_1"), "running id cannot start again");
        store.finishRun("cmd_1", CommandStore.Outgoing.ack("cmd_1", "executed"));
        assertFalse(store.beginRun("cmd_1"));

        CommandStore reopened = new CommandStore(file);
        assertTrue(reopened.isProcessed("cmd_1"));
        assertFalse(reopened.beginRun("cmd_1"));
        assertEquals(1, reopened.outgoingCount(), "unsent ACK is kept on disk");
    }

    @Test
    void interruptedRunIsReportedNotRepeated() {
        Path file = dir.resolve("commands.json");
        CommandStore store = new CommandStore(file);
        assertTrue(store.beginRun("cmd_crash")); // sunucu burada çöktü

        CommandStore reopened = new CommandStore(file);
        assertFalse(reopened.beginRun("cmd_crash"));
        assertEquals(List.of(CommandStore.Outgoing.nack("cmd_crash", CommandStore.REASON_INTERRUPTED)), reopened.outgoing());
    }

    @Test
    void processedIdsAreKeptAtLeastADayThenPruned() {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        Path file = dir.resolve("commands.json");
        CommandStore store = new CommandStore(file, null, now::get, null);
        store.beginRun("old");
        store.finishRun("old", null);

        now.addAndGet(24L * 60 * 60 * 1000 + 1);
        assertTrue(new CommandStore(file, null, now::get, null).isProcessed("old"));

        now.addAndGet(CommandStore.PROCESSED_RETENTION_MS);
        assertFalse(new CommandStore(file, null, now::get, null).isProcessed("old"));
    }

    @Test
    void outboxRemovesOnlySentEntries() {
        CommandStore store = new CommandStore(null);
        CommandStore.Outgoing a = CommandStore.Outgoing.ack("a", "queued");
        CommandStore.Outgoing b = CommandStore.Outgoing.nack("b", "x");
        store.addOutgoing(a);
        store.addOutgoing(b);
        store.removeOutgoing(List.of(a));
        assertEquals(List.of(b), store.outgoing());
    }

    @Test
    void migratesTask273FileAndRestrictsPermissions() throws Exception {
        Path legacy = dir.resolve("pending-commands.json");
        Files.writeString(legacy, "{\"queue\":{\"Notch\":[{\"commandId\":\"cmd_q\",\"command\":\"give %player% x\"}]},\"executedIds\":[\"cmd_done\"]}");
        Path file = dir.resolve("data/commands.json");
        CommandStore store = new CommandStore(file, legacy, null, null);
        assertTrue(store.isQueued("cmd_q"));
        assertTrue(store.isProcessed("cmd_done"));
        assertFalse(Files.exists(legacy));
        assertEquals(1, store.drainFor("notch", null).size());
        if (Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
            assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.getParent())));
        }
    }

    @Test
    void corruptFileIsKeptAside() throws Exception {
        Path file = dir.resolve("commands.json");
        Files.writeString(file, "{not json");
        CommandStore store = new CommandStore(file);
        assertEquals(0, store.queuedCount());
        try (var s = Files.list(dir)) {
            assertTrue(s.anyMatch(p -> p.getFileName().toString().startsWith("commands.json.corrupt-")));
        }
    }
}
