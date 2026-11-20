package net.crafter.plugin.core;

import net.crafter.plugin.core.model.PendingCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PendingCommandStoreTest {

    private static PendingCommand command(String id, String player) {
        PendingCommand pc = new PendingCommand();
        pc.setCommandId(id);
        pc.setPlayer(player);
        pc.setCommand("give " + player + " diamond 1");
        return pc;
    }

    @Test
    void queueSurvivesRestartAndMatchesCaseInsensitively(@TempDir Path dir) {
        Path file = dir.resolve("pending-commands.json");
        PendingCommandStore store = new PendingCommandStore(file);
        store.enqueue("Steve", command("cmd_1", "Steve"));
        store.enqueue("STEVE", command("cmd_2", "Steve"));
        assertTrue(store.isQueued("cmd_1"));

        PendingCommandStore reloaded = new PendingCommandStore(file);
        assertEquals(2, reloaded.size());
        List<PendingCommand> drained = reloaded.drain("steve");
        assertEquals(List.of("cmd_1", "cmd_2"), drained.stream().map(PendingCommand::getCommandId).toList());
        // Orijinal yazım korunur
        assertEquals("give Steve diamond 1", drained.get(0).resolveCommand());
        assertEquals(0, new PendingCommandStore(file).size());
    }

    @Test
    void executedIdsArePersistedForDeduplication(@TempDir Path dir) {
        Path file = dir.resolve("pending-commands.json");
        new PendingCommandStore(file).markExecuted("cmd_9");
        assertTrue(new PendingCommandStore(file).isExecuted("cmd_9"));
        assertFalse(new PendingCommandStore(file).isExecuted("cmd_10"));
    }

    @Test
    void inMemoryStoreWorksWithoutFile() {
        PendingCommandStore store = new PendingCommandStore(null);
        store.enqueue("Alex", command("cmd_3", "Alex"));
        assertEquals(1, store.size());
        assertEquals(1, store.drain("alex").size());
    }
}
