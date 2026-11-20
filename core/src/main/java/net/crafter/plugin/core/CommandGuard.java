package net.crafter.plugin.core;

import net.crafter.plugin.core.model.PendingCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Konsolda çalışacak komutun ikinci savunma hattı (TASK-286). Backend değerleri zaten doğrular; buradaki
 * kontroller backend'de bir açık kalırsa konsola zararlı komut gitmesini engeller.
 *
 * - Oyuncu adı Minecraft adı desenine uymuyorsa ({@code [A-Za-z0-9_]{3,16}}, isteğe bağlı tek karakterlik
 *   Bedrock/Floodgate öneki) komut çalışmaz.
 * - Satır sonu ya da başka bir kontrol karakteri içeren komut çalışmaz.
 * - {@code allowed-command-prefixes} doluysa yalnızca listedeki öneklerle başlayan komutlar çalışır.
 */
public final class CommandGuard {

    public static final String REASON_INVALID_PLAYER = "invalid_player";
    public static final String REASON_CONTROL_CHARACTER = "control_character";
    public static final String REASON_PREFIX_NOT_ALLOWED = "prefix_not_allowed";

    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029\\u0085]");
    private static final String NAME_BODY = "[A-Za-z0-9_]{3,16}";
    /** Bedrock/Floodgate öneki için izin verilen tek karakterler */
    private static final String PREFIX_CHARACTERS = ".*!~+-#";

    private final Pattern playerPattern;
    private final List<String> allowedPrefixes;

    public CommandGuard(String bedrockPrefix, List<String> allowedPrefixes) {
        String prefix = bedrockPrefix == null ? "" : bedrockPrefix.trim();
        boolean usePrefix = prefix.length() == 1 && PREFIX_CHARACTERS.contains(prefix);
        this.playerPattern = Pattern.compile(
                usePrefix ? "^" + Pattern.quote(prefix) + "?" + NAME_BODY + "$" : "^" + NAME_BODY + "$");
        List<String> normalized = new ArrayList<>();
        if (allowedPrefixes != null) {
            for (String entry : allowedPrefixes) {
                String value = normalize(entry);
                if (!value.isEmpty()) normalized.add(value.toLowerCase(Locale.ROOT));
            }
        }
        this.allowedPrefixes = Collections.unmodifiableList(normalized);
    }

    /** Oyuncu adı Minecraft adı desenine uyuyor mu */
    public boolean isValidPlayer(String player) {
        return player != null && playerPattern.matcher(player).matches();
    }

    /**
     * Komutun çalıştırılıp çalıştırılamayacağını kontrol eder.
     * @return null → çalıştırılabilir; aksi halde NACK nedeni
     */
    public String check(PendingCommand pc) {
        String player = pc.getPlayer();
        if (player != null && !player.isEmpty() && !isValidPlayer(player)) {
            return REASON_INVALID_PLAYER;
        }
        String resolved = pc.resolveCommand();
        if (resolved == null) return null;
        if (CONTROL_CHARACTER.matcher(resolved).find()) {
            return REASON_CONTROL_CHARACTER;
        }
        if (!allowedPrefixes.isEmpty() && !isAllowedPrefix(resolved)) {
            return REASON_PREFIX_NOT_ALLOWED;
        }
        return null;
    }

    private boolean isAllowedPrefix(String command) {
        String normalized = normalize(command).toLowerCase(Locale.ROOT);
        for (String prefix : allowedPrefixes) {
            // Kelime sınırında eşleşme: "give" öneki "giveall" komutuna izin vermez
            if (normalized.equals(prefix) || normalized.startsWith(prefix + " ")) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        while (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        return trimmed.trim();
    }
}
