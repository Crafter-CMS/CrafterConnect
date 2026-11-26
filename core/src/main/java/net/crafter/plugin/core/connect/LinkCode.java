package net.crafter.plugin.core.connect;

import java.util.Locale;

/**
 * Link code from the panel (#369 3.2): 8 Crockford base32 characters (no I L O U), shown as "K7QM-4XPD".
 * Comparison ignores case, dashes and spaces; O is read as 0, I/L as 1 (same rule as the backend).
 */
public final class LinkCode {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LENGTH = 8;

    private LinkCode() {}

    /** Null if invalid */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String cleaned = raw.toUpperCase(Locale.ROOT)
                .replaceAll("[\\s-]", "")
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1');
        if (cleaned.length() != LENGTH) return null;
        for (char c : cleaned.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) return null;
        }
        return cleaned;
    }

    public static String format(String normalized) {
        return normalized.substring(0, 4) + "-" + normalized.substring(4);
    }
}
