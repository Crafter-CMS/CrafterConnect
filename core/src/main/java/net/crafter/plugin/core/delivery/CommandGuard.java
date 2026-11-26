package net.crafter.plugin.core.delivery;

import net.crafter.plugin.core.model.PendingCommand;
import net.crafter.plugin.core.model.PlayerIdentity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Konsolda tam yetkiyle çalışacak komutun ikinci savunma hattı (#286, #37 bölüm 3.4). Backend değerleri zaten
 * doğrular; buradaki kontroller backend'de bir açık kalsa bile konsola zararlı komut gitmesini engeller.
 *
 * <ul>
 *   <li>Oyuncu adı backend'in teslimat kuralına uymalı (backend {@code isDeliverableUsername}, #247): Java adı
 *   {@code [A-Za-z0-9_]{1,16}}; Bedrock/Floodgate adı {@code .} (ya da sunucudaki Floodgate öneki) ile başlar ve
 *   kelimeler arasında tek boşluk olabilir (en çok 17 karakter). Öneksiz boşluklu ad yalnızca kimlik türü
 *   minecraft_bedrock ise kabul edilir. Tırnak, {@code \ ; & | $ ` @ %}, satır sonu ve kontrol karakteri hiçbir
 *   durumda geçmez. Boşluklu ad {@code %player%} yerine çift tırnak içinde tek argüman olarak yazılır.</li>
 *   <li>Kimlik ({@code identity}) kendi türünün desenine uymalı (UUID yalnızca hex).</li>
 *   <li>Satır sonu ya da başka bir kontrol karakteri içeren komut çalışmaz.</li>
 *   <li>İzinli önek listesi: yerel {@code allowed-command-prefixes} ve backend'in {@code settings.allowedCommandPrefixes}
 *   listesinin ikisi de dolu ise komut ikisinden de geçmelidir (kesişim); yalnızca biri doluysa o geçerlidir.</li>
 * </ul>
 * Uymayan komut çalıştırılmaz, NACK gönderilir ve sipariş incelemeye düşer.
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
    /** Floodgate'in varsayılan öneki; backend de her zaman kabul eder */
    public static final String DEFAULT_FLOODGATE_PREFIX = ".";
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{32})$");
    /** Floodgate öneki olarak kabul edilen tek karakterler (backend de tek karakter saklar) */
    public static final String PREFIX_CHARACTERS = ".*!~+-#";

    private final List<String> bedrockPrefixes;
    private final String serverIdentityType;
    private final List<String> localPrefixes;
    private final List<String> remotePrefixes;

    /**
     * @param floodgatePrefix    sunucudaki Floodgate öneki (yoksa null); "." her zaman kabul edilir
     * @param serverIdentityType auth_success'teki identityType (minecraft_java | minecraft_bedrock), bilinmiyorsa null
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

    /** Oyuncu adı sunucunun kimlik türüne göre teslim edilebilir mi */
    public boolean isValidPlayer(String player) {
        return isDeliverable(player, serverIdentityType);
    }

    /** Backend {@code isDeliverableUsername} ile aynı kural (Minecraft türleri) */
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
     * @return null → çalıştırılabilir; aksi halde NACK nedeni
     */
    public String check(PendingCommand pc) {
        if (pc.getCommand() == null || pc.getCommand().isBlank()) return REASON_EMPTY_COMMAND;
        // Ham şablon da kontrol edilir: değerler eklenmeden önce satır sonu taşıyorsa reddedilir
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
            // Backend sunucunun türünü yazar; Floodgate adı minecraft_java türüyle de gelebilir
            case PlayerIdentity.MINECRAFT_JAVA, PlayerIdentity.MINECRAFT_BEDROCK -> isDeliverable(value, type);
            default -> false; // Minecraft sunucusunda başka kimlik türü beklenmez
        };
    }

    private String effectiveIdentityType(PendingCommand pc) {
        PlayerIdentity id = pc.getIdentity();
        if (id != null && PlayerIdentity.MINECRAFT_BEDROCK.equals(id.getType())) return PlayerIdentity.MINECRAFT_BEDROCK;
        return serverIdentityType;
    }

    /** Liste boşsa kontrol yok; doluysa kelime sınırında önek eşleşmesi ("give" → "giveall" geçmez) */
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
