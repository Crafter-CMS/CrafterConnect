package net.crafter.plugin.core;

import net.crafter.plugin.core.model.PendingCommand;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class CommandGuardTest {

    private static PendingCommand command(String command, String player) {
        PendingCommand pc = new PendingCommand();
        pc.setCommandId("cmd-1");
        pc.setCommand(command);
        pc.setPlayer(player);
        return pc;
    }

    private final CommandGuard guard = new CommandGuard("", Collections.emptyList());

    @Test
    void validPlayerAndCommandPass() {
        assertNull(guard.check(command("lp user %player% parent add vip", "Steve_01")));
        assertNull(guard.check(command("say hello", null)));
        assertNull(guard.check(command("say hello", "")));
    }

    @Test
    void invalidPlayerNamesAreRejected() {
        assertEquals(CommandGuard.REASON_INVALID_PLAYER, guard.check(command("lp user %player% parent add vip", "Steve parent add admin")));
        assertEquals(CommandGuard.REASON_INVALID_PLAYER, guard.check(command("give %player% diamond 1", "@a")));
        assertEquals(CommandGuard.REASON_INVALID_PLAYER, guard.check(command("give %player% diamond 1", "ab")));
        assertEquals(CommandGuard.REASON_INVALID_PLAYER, guard.check(command("give %player% diamond 1", "a_very_long_player_name")));
        assertEquals(CommandGuard.REASON_INVALID_PLAYER, guard.check(command("give %player% diamond 1", "Steve\nop Steve")));
    }

    @Test
    void lineBreaksAndControlCharactersAreRejected() {
        assertEquals(CommandGuard.REASON_CONTROL_CHARACTER, guard.check(command("say hi\nop Steve", "Steve")));
        assertEquals(CommandGuard.REASON_CONTROL_CHARACTER, guard.check(command("say hi\rop Steve", null)));
        assertEquals(CommandGuard.REASON_CONTROL_CHARACTER, guard.check(command("say hi\u0000", null)));
    }

    @Test
    void bedrockPrefixIsOptIn() {
        assertEquals(CommandGuard.REASON_INVALID_PLAYER, guard.check(command("give %player% diamond 1", ".BedrockGuy")));
        CommandGuard withPrefix = new CommandGuard(".", Collections.emptyList());
        assertNull(withPrefix.check(command("give %player% diamond 1", ".BedrockGuy")));
        assertNull(withPrefix.check(command("give %player% diamond 1", "JavaGuy")));
        assertEquals(CommandGuard.REASON_INVALID_PLAYER, withPrefix.check(command("give %player% diamond 1", "..Bedrock")));
        // Desteklenmeyen önek karakteri yok sayılır
        CommandGuard badPrefix = new CommandGuard("@", Collections.emptyList());
        assertEquals(CommandGuard.REASON_INVALID_PLAYER, badPrefix.check(command("give %player% x", "@Steve")));
    }

    @Test
    void allowedPrefixesRestrictCommands() {
        CommandGuard restricted = new CommandGuard("", Arrays.asList("lp user", "/give", " "));
        assertNull(restricted.check(command("lp user %player% parent add vip", "Steve")));
        assertNull(restricted.check(command("/give %player% diamond 1", "Steve")));
        assertNull(restricted.check(command("LP USER Steve parent add vip", null)));
        assertEquals(CommandGuard.REASON_PREFIX_NOT_ALLOWED, restricted.check(command("op %player%", "Steve")));
        assertEquals(CommandGuard.REASON_PREFIX_NOT_ALLOWED, restricted.check(command("giveall diamond", null)));
    }

    @Test
    void emptyAllowListKeepsCurrentBehaviour() {
        assertNull(guard.check(command("op %player%", "Steve")));
        assertNull(new CommandGuard(null, null).check(command("op %player%", "Steve")));
    }
}
