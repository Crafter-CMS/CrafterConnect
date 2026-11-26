package net.crafter.plugin.core.delivery;

import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.model.PlayerIdentity;
import net.crafter.plugin.core.support.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommandProcessorTest {

    private Fakes.FakePlatform platform;
    private CommandStore store;
    private CommandProcessor processor;
    private final List<CommandStore.Outgoing> sent = new ArrayList<>();
    private boolean connected = true;

    @BeforeEach
    void setUp() {
        platform = new Fakes.FakePlatform();
        store = new CommandStore(null);
        processor = new CommandProcessor(store, platform,
                () -> new CommandGuard(null, "minecraft_java", List.of(), List.of()), platform.log);
        processor.setSender(o -> {
            if (!connected) return false;
            sent.add(o);
            return true;
        });
    }

    private static PendingCommand cmd(String id, String command, String player) {
        return new PendingCommand(id, command, player);
    }

    @Test
    void onlinePlayerCommandRunsOnceAndIsAcked() {
        platform.online.add("Notch");
        processor.onCommand(cmd("cmd_1", "give %player% diamond 1", "notch"));
        assertEquals(List.of("give notch diamond 1"), platform.executed);
        assertEquals(List.of(CommandStore.Outgoing.ack("cmd_1", "executed")), sent);

        // Backend ACK'i alamadı ve aynı id'yi yeniden gönderdi
        processor.onCommand(cmd("cmd_1", "give %player% diamond 1", "notch"));
        assertEquals(1, platform.executed.size(), "same commandId must not run twice");
        assertEquals(CommandStore.Outgoing.ack("cmd_1", "executed"), sent.get(1));
    }

    @Test
    void duplicateWhileStillRunningOnMainThreadIsIgnored() {
        platform.online.add("Notch");
        platform.defer = true;
        processor.onCommand(cmd("cmd_1", "give Notch x", "Notch"));
        processor.onCommand(cmd("cmd_1", "give Notch x", "Notch"));
        platform.runDeferred();
        assertEquals(1, platform.executed.size());
        assertEquals(List.of(CommandStore.Outgoing.ack("cmd_1", "executed")), sent);
    }

    @Test
    void offlinePlayerIsQueuedThenRunOnJoin() {
        processor.onCommand(cmd("cmd_1", "give %player% diamond 1", "Notch"));
        assertTrue(platform.executed.isEmpty());
        assertEquals(List.of(CommandStore.Outgoing.ack("cmd_1", "queued")), sent);

        processor.onCommand(cmd("cmd_1", "give %player% diamond 1", "Notch")); // tekrar gönderim
        assertEquals(CommandStore.Outgoing.ack("cmd_1", "queued"), sent.get(1));
        assertEquals(1, store.queuedCount());

        processor.onPlayerJoin("NOTCH", UUID.randomUUID()); // eşleme harf duyarsız; %player% oyundaki yazımla
        assertEquals(List.of("give NOTCH diamond 1"), platform.executed);
        assertEquals(CommandStore.Outgoing.ack("cmd_1", "executed"), sent.get(2));
    }

    @Test
    void verifiedUuidCountsAsOnlineAfterNameChange() {
        UUID uuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        platform.onlineUuids.add(uuid);
        PendingCommand pc = cmd("cmd_1", "lp user OldName parent add vip", "OldName");
        pc.setIdentity(new PlayerIdentity("minecraft_uuid", uuid.toString()));
        processor.onCommand(pc);
        assertEquals(1, platform.executed.size());
    }

    @Test
    void floodgateSpacesAreMatchedAsUnderscores() {
        platform.online.add(".Cool_Gamer");
        processor.onCommand(cmd("cmd_1", "lp user %player% parent add vip", ".Cool Gamer"));
        assertEquals(List.of("lp user .Cool_Gamer parent add vip"), platform.executed);
    }

    @Test
    void queuedCommandUsesInGameNameOnJoin() {
        processor.onCommand(cmd("cmd_1", "lp user %player% parent add vip", ".Cool Gamer"));
        processor.onPlayerJoin(".Cool_Gamer", null);
        assertEquals(List.of("lp user .Cool_Gamer parent add vip"), platform.executed);
    }

    @Test
    void requireOnlineFalseRunsImmediately() {
        PendingCommand pc = cmd("cmd_1", "lp user Notch parent add vip", "Notch");
        pc.setRequireOnline(false);
        processor.onCommand(pc);
        assertEquals(1, platform.executed.size());
    }

    @Test
    void guardRejectionIsNackedAndNeverRetried() {
        platform.online.add("Notch");
        processor.onCommand(cmd("cmd_1", "give Notch x\nop Notch", "Notch"));
        assertTrue(platform.executed.isEmpty());
        assertEquals(List.of(CommandStore.Outgoing.nack("cmd_1", CommandGuard.REASON_CONTROL_CHARACTER)), sent);
        processor.onCommand(cmd("cmd_1", "give Notch x\nop Notch", "Notch"));
        assertTrue(platform.executed.isEmpty());
    }

    @Test
    void rejectedCommandResentWithSameIdGetsTheSameNack() {
        platform.online.add("Notch");
        processor.onCommand(cmd("cmd_p", "op Notch", "Notch"));            // önek listesi yok: geçer, ama:
        sent.clear();
        CommandProcessor strict = new CommandProcessor(store, platform,
                () -> new CommandGuard(null, "minecraft_java", List.of("give"), List.of()), platform.log);
        strict.setSender(sent::add);
        strict.onCommand(cmd("cmd_n", "op Notch", "Notch"));
        strict.onCommand(cmd("cmd_n", "op Notch", "Notch"));                 // backend tekrar gönderdi
        CommandStore.Outgoing nack = CommandStore.Outgoing.nack("cmd_n", CommandGuard.REASON_PREFIX_NOT_ALLOWED);
        assertEquals(List.of(nack, nack), sent, "a rejected command must never be acked as executed");
        assertEquals(List.of("op Notch"), platform.executed);
    }

    @Test
    void failedExecutionResentKeepsNack() {
        platform.online.add("Notch");
        platform.result = false;
        processor.onCommand(cmd("cmd_f", "unknowncmd Notch", "Notch"));
        platform.result = true;
        processor.onCommand(cmd("cmd_f", "unknowncmd Notch", "Notch"));
        CommandStore.Outgoing nack = CommandStore.Outgoing.nack("cmd_f", CommandProcessor.REASON_EXECUTION_FAILED);
        assertEquals(List.of(nack, nack), sent);
        assertEquals(1, platform.executed.size());
    }

    @Test
    void failedExecutionIsNacked() {
        platform.online.add("Notch");
        platform.result = false;
        processor.onCommand(cmd("cmd_1", "unknowncmd Notch", "Notch"));
        assertEquals(List.of(CommandStore.Outgoing.nack("cmd_1", CommandProcessor.REASON_EXECUTION_FAILED)), sent);
    }

    @Test
    void acksWaitInOutboxWhileDisconnectedAndFlushInOrder() {
        connected = false;
        processor.onCommand(cmd("cmd_q", "give %player% x", "Notch"));        // queued
        platform.online.add("Other");
        processor.onCommand(cmd("cmd_r", "say hi", null));                    // executed
        processor.onPlayerJoin("Notch", null);                                 // executed
        assertTrue(sent.isEmpty());
        assertEquals(3, store.outgoingCount());

        connected = true;
        processor.flush();
        assertEquals(List.of(
                CommandStore.Outgoing.ack("cmd_q", "queued"),
                CommandStore.Outgoing.ack("cmd_r", "executed"),
                CommandStore.Outgoing.ack("cmd_q", "executed")), sent);
        assertEquals(0, store.outgoingCount());
    }

    @Test
    void commandTextAndPlayerAreNotLogged() {
        platform.online.add("SecretName");
        processor.onCommand(cmd("cmd_1", "lp user SecretName parent add vip", "SecretName"));
        processor.onCommand(cmd("cmd_2", "give SecretName x\nop", "SecretName"));
        assertFalse(platform.log.all().contains("SecretName"));
        assertTrue(platform.log.all().contains("cmd_1"));
    }
}
