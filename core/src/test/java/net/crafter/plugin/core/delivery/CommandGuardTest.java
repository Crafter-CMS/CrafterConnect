package net.crafter.plugin.core.delivery;

import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.model.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandGuardTest {

    private static PendingCommand cmd(String command, String player) {
        return new PendingCommand("cmd_1", command, player);
    }

    private final CommandGuard guard = new CommandGuard(null, "minecraft_java", List.of(), List.of());

    @Test
    void acceptsJavaNamesLikeBackend() {
        assertNull(guard.check(cmd("lp user %player% parent add vip", "Notch")));
        assertNull(guard.check(cmd("give %player% diamond 1", "a"))); // backend: {1,16}
        assertNull(guard.check(cmd("give %player% diamond 1", "Abcdefghijklmnop")));
        assertEquals(CommandGuard.REASON_PLAYER_INVALID, guard.check(cmd("give %player% x", "Abcdefghijklmnopq")));
    }

    @Test
    void acceptsFloodgateNamesWithDefaultPrefixAndSpaces() {
        assertNull(guard.check(cmd("give %player% diamond 1", ".Steve")));
        assertNull(guard.check(cmd("lp user %player% parent add vip", ".Cool Gamer")));
        // A spaced name without a prefix is allowed only for a Bedrock identity
        assertEquals(CommandGuard.REASON_PLAYER_INVALID, guard.check(cmd("give %player% x", "Cool Gamer")));
        PendingCommand bedrock = cmd("give %player% x", "Cool Gamer");
        bedrock.setIdentity(new PlayerIdentity("minecraft_bedrock", "Cool Gamer"));
        assertNull(guard.check(bedrock));
        assertNull(new CommandGuard(null, "minecraft_bedrock", List.of(), List.of()).check(cmd("give %player% x", "Cool Gamer")));
    }

    @Test
    void configuredFloodgatePrefixIsAcceptedTogetherWithDot() {
        CommandGuard g = new CommandGuard("*", "minecraft_java", List.of(), List.of());
        assertNull(g.check(cmd("give %player% x", "*Steve")));
        assertNull(g.check(cmd("give %player% x", ".Steve")));
        assertEquals(CommandGuard.REASON_PLAYER_INVALID, g.check(cmd("give %player% x", "!Steve")));
        // An invalid prefix setting is ignored
        assertFalse(CommandGuard.isValidFloodgatePrefix(".."));
        assertFalse(CommandGuard.isValidFloodgatePrefix(";"));
    }

    @Test
    void rejectsSeparatorsQuotesSelectorsAndDoubleSpaces() {
        for (String bad : new String[]{"Notch;op", "Notch&x", "a|b", "\"Notch\"", "'x'", "@a", "@p", "a\\b", "$x", "`x`",
                "a%b", ".Cool  Gamer", ".Cool Gamer ", ". Cool", "Notch\nop Notch", "Notch\rx", "Ünal", ""}) {
            if (bad.isEmpty()) continue;
            assertEquals(CommandGuard.REASON_PLAYER_INVALID, guard.check(cmd("give %player% x", bad)), bad);
        }
    }

    @Test
    void quotesBedrockNameWithSpacesAsOneArgument() {
        PendingCommand pc = cmd("lp user %player% parent add vip", ".Cool Gamer");
        assertNull(guard.check(pc));
        assertEquals("lp user \".Cool Gamer\" parent add vip", pc.resolveCommand());
        assertEquals("give Notch diamond 1", cmd("/give %player% diamond 1", "Notch").resolveCommand());
    }

    @Test
    void rejectsLineBreaksAndControlCharactersInCommand() {
        assertEquals(CommandGuard.REASON_CONTROL_CHARACTER, guard.check(cmd("give Notch x\nop Notch", "Notch")));
        assertEquals(CommandGuard.REASON_CONTROL_CHARACTER, guard.check(cmd("give Notch x\r", null)));
        assertEquals(CommandGuard.REASON_CONTROL_CHARACTER, guard.check(cmd("say a op b", null)));
        assertEquals(CommandGuard.REASON_CONTROL_CHARACTER, guard.check(cmd("say a\u0000", null)));
        assertEquals(CommandGuard.REASON_EMPTY_COMMAND, guard.check(cmd("   ", null)));
        assertEquals(CommandGuard.REASON_EMPTY_COMMAND, guard.check(cmd("/", null)));
    }

    @Test
    void playerPlaceholderWithoutPlayerIsRejected() {
        assertEquals(CommandGuard.REASON_PLAYER_INVALID, guard.check(cmd("give %player% x", null)));
        assertNull(guard.check(cmd("say hello", null)));
    }

    @Test
    void identityIsValidatedByType() {
        PendingCommand pc = cmd("lp user Notch parent add vip", "Notch");
        pc.setIdentity(new PlayerIdentity("minecraft_uuid", "069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertNull(guard.check(pc));
        pc.setIdentity(new PlayerIdentity("minecraft_uuid", "069a79f444e94726a5befca90e38aaf5"));
        assertNull(guard.check(pc));
        pc.setIdentity(new PlayerIdentity("minecraft_uuid", "069a79f4 op"));
        assertEquals(CommandGuard.REASON_PLAYER_INVALID, guard.check(pc));
        pc.setIdentity(new PlayerIdentity("minecraft_java", ".Cool Gamer")); // the backend writes the server type
        assertNull(guard.check(pc));
        pc.setIdentity(new PlayerIdentity("steam64", "76561198000000000"));
        assertEquals(CommandGuard.REASON_PLAYER_INVALID, guard.check(pc));
    }

    @Test
    void prefixListsAreIntersected() {
        PendingCommand lp = cmd("lp user Notch parent add vip", "Notch");
        PendingCommand give = cmd("give Notch diamond 1", "Notch");
        PendingCommand giveall = cmd("giveall diamond", null);

        CommandGuard local = new CommandGuard(null, List.of("lp user", "/give"), List.of());
        assertNull(local.check(lp));
        assertNull(local.check(give));
        assertEquals(CommandGuard.REASON_PREFIX_NOT_ALLOWED, local.check(giveall));

        CommandGuard remote = new CommandGuard(null, List.of(), List.of("LP"));
        assertNull(remote.check(lp));
        assertEquals(CommandGuard.REASON_PREFIX_NOT_ALLOWED, remote.check(give));

        CommandGuard both = new CommandGuard(null, List.of("lp", "give"), List.of("lp user", "eco"));
        assertNull(both.check(lp));
        assertEquals(CommandGuard.REASON_PREFIX_NOT_ALLOWED, both.check(give));
        assertEquals(CommandGuard.REASON_PREFIX_NOT_ALLOWED, both.check(cmd("eco give Notch 5", "Notch")));

        // A namespaced command prefix counts as different (safe side)
        assertEquals(CommandGuard.REASON_PREFIX_NOT_ALLOWED, local.check(cmd("minecraft:give Notch x", "Notch")));
    }
}
