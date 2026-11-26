package net.crafter.plugin.core.connect;

import java.util.Locale;

/**
 * Paneldeki bağlantı kodu (#369 3.2): 8 karakter Crockford base32 (I L O U yok), "K7QM-4XPD" biçiminde gösterilir.
 * Karşılaştırma büyük/küçük harf, tire ve boşluk duyarsızdır; O → 0, I/L → 1 okunur (backend ile aynı kural).
 */
public final class LinkCode {

    public static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    public static final int LENGTH = 8;

    private LinkCode() {}

    /** Geçersizse null */
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
