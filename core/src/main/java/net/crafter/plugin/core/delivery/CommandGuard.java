package net.crafter.plugin.core.delivery;

import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.model.PlayerIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Second line of defense for a command that will run on the console with full permissions (#286, #37 section 3.4). The backend
 * already validates the values; these checks stop a harmful command reaching the console even if the backend has a hole.
 *
 * <ul>
 *   <li>The player name must follow the backend's delivery rule (backend {@code isDeliverableUsername}, #247): a Java name is
 *   {@code [A-Za-z0-9_]{1,16}}; a Bedrock/Floodgate name starts with {@code .} (or the server's Floodgate prefix) and
 *   may have single spaces between words (at most 17 characters). A spaced name without a prefix is accepted only when the
 *   identity type is minecraft_bedrock. Quotes, {@code \ ; & | $ ` @ %}, line breaks and control characters are never
 *   allowed. A spaced name is written in place of {@code %player%} as a single double-quoted argument.</li>
 *   <li>The identity ({@code identity}) must match its type's pattern (UUID is hex only).</li>
 *   <li>A command containing a line break or any other control character never runs.</li>
 *   <li>Allowed prefix list: if both the local {@code allowed-command-prefixes} and the backend's {@code settings.allowedCommandPrefixes}
 *   are set, the command must pass both (intersection); if only one is set, that one applies.</li>
 * </ul>
 * A non-matching command is not run, a NACK is sent and the order goes to review.
 */
public final class CommandGuard {

    public static final String REASON_PLAYER_INVALID = "player_identity_invalid";
    public static final String REASON_CONTROL_CHARACTER = "control_character";
    public static final String REASON_PREFIX_NOT_ALLOWED = "prefix_not_allowed";
    public static final String REASON_EMPTY_COMMAND = "empty_command";

    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029\\u0085]");
    private static final Pattern JAVA_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern MC_WORDS = Pattern.compile("^[A-Za-z0-9_]+(?: [A-Za-z0-9_]+)*$");
    private static final int MAX_NAME_LENGTH = 17;
    /** Floodgate's default prefix; the backend always accepts it too */
    public static final String DEFAULT_FLOODGATE_PREFIX = ".";
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{32})$");
    /** Single characters accepted as a Floodgate prefix (the backend also stores a single character) */
    public static final String PREFIX_CHARACTERS = ".*!~+-#";

    private final List<String> bedrockPrefixes;
    private final String serverIdentityType;
    private final List<String> localPrefixes;
    private final List<String> remotePrefixes;

    /**
     * @param floodgatePrefix    the server's Floodgate prefix (null if none); "." is always accepted
     * @param serverIdentityType identityType from auth_success (minecraft_java | minecraft_bedrock), null if unknown
     */
    public CommandGuard(String floodgatePrefix, String serverIdentityType, List<String> localPrefixes, List<String> remotePrefixes) {
        List<String> prefixes = new ArrayList<>();
        prefixes.add(DEFAULT_FLOODGATE_PREFIX);
        if (isValidFloodgatePrefix(floodgatePrefix) && !DEFAULT_FLOODGATE_PREFIX.equals(floodgatePrefix.trim())) {
            prefixes.add(floodgatePrefix.trim());
        }
        this.bedrockPrefixes = Collections.unmodifiableList(prefixes);
        this.serverIdentityType = serverIdentityType;
        this.localPrefixes = normalizeAll(localPrefixes);
        this.remotePrefixes = normalizeAll(remotePrefixes);
    }

    public CommandGuard(String floodgatePrefix, List<String> localPrefixes, List<String> remotePrefixes) {
        this(floodgatePrefix, null, localPrefixes, remotePrefixes);
    }

    public static boolean isValidFloodgatePrefix(String prefix) {
        if (prefix == null) return false;
        String p = prefix.trim();
        return p.length() == 1 && PREFIX_CHARACTERS.contains(p);
    }

    /** Whether the player name is deliverable for the server's identity type */
    public boolean isValidPlayer(String player) {
        return isDeliverable(player, serverIdentityType);
    }

    /** Same rule as the backend's {@code isDeliverableUsername} (Minecraft types) */
    public boolean isDeliverable(String value, String identityType) {
        if (value == null || value.isEmpty() || value.length() > MAX_NAME_LENGTH) return false;
        if (JAVA_NAME.matcher(value).matches()) return true;
        String prefix = null;
        for (String p : bedrockPrefixes) {
            if (value.startsWith(p)) { prefix = p; break; }
        }
        if (prefix == null && !PlayerIdentity.MINECRAFT_BEDROCK.equals(identityType)) return false;
        String body = prefix == null ? value : value.substring(prefix.length());
        return MC_WORDS.matcher(body).matches();
    }

    /**
     * @return null → runnable; otherwise the NACK reason
     */
    public String check(PendingCommand pc) {
        if (pc.getCommand() == null || pc.getCommand().isBlank()) return REASON_EMPTY_COMMAND;
        // The raw template is checked too: rejected if it carries a line break before values are filled in
        if (CONTROL_CHARACTER.matcher(pc.getCommand()).find()) return REASON_CONTROL_CHARACTER;

        String type = effectiveIdentityType(pc);
        if (pc.hasPlayer() && !isDeliverable(pc.getPlayer(), type)) return REASON_PLAYER_INVALID;
        if (!pc.hasPlayer() && pc.getCommand().contains(PendingCommand.PLAYER_PLACEHOLDER)) return REASON_PLAYER_INVALID;
        if (pc.getIdentity() != null && !isValidIdentity(pc.getIdentity())) return REASON_PLAYER_INVALID;

        String resolved = pc.resolveCommand();
        if (resolved == null || resolved.isBlank()) return REASON_EMPTY_COMMAND;
        if (CONTROL_CHARACTER.matcher(resolved).find()) return REASON_CONTROL_CHARACTER;
        if (!matches(localPrefixes, resolved) || !matches(remotePrefixes, resolved)) return REASON_PREFIX_NOT_ALLOWED;
        return null;
    }

    boolean isValidIdentity(PlayerIdentity identity) {
        String type = identity.getType();
        String value = identity.getValue();
        if (type == null || value == null) return false;
        return switch (type) {
            case PlayerIdentity.MINECRAFT_UUID -> UUID_PATTERN.matcher(value).matches();
            // The backend writes the server's type; a Floodgate name may also arrive with the minecraft_java type
            case PlayerIdentity.MINECRAFT_JAVA, PlayerIdentity.MINECRAFT_BEDROCK -> isDeliverable(value, type);
            default -> false; // no other identity type is expected on a Minecraft server
        };
    }

    private String effectiveIdentityType(PendingCommand pc) {
        PlayerIdentity id = pc.getIdentity();
        if (id != null && PlayerIdentity.MINECRAFT_BEDROCK.equals(id.getType())) return PlayerIdentity.MINECRAFT_BEDROCK;
        return serverIdentityType;
    }

    /** No check if the list is empty; otherwise prefix match on a word boundary ("give" does not pass "giveall") */
    private static boolean matches(List<String> prefixes, String command) {
        if (prefixes.isEmpty()) return true;
        String normalized = normalize(command).toLowerCase(Locale.ROOT);
        for (String prefix : prefixes) {
            if (normalized.equals(prefix) || normalized.startsWith(prefix + " ")) return true;
        }
        return false;
    }

    private static List<String> normalizeAll(List<String> entries) {
        List<String> out = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                String value = normalize(entry).toLowerCase(Locale.ROOT);
                if (!value.isEmpty()) out.add(value);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1).trim();
        return trimmed;
    }
}
