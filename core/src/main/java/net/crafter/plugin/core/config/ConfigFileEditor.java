package net.crafter.plugin.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * config.yml'da tek bir üst düzey değeri satır bazında değiştirir; yorumlar ve diğer ayarlar korunur.
 * Bukkit/Configurate ile kaydetmek yorumları silebildiği için {@code /crafter connect} token'ı bu sınıfla yazar.
 * Dosya her yazımda 600 izniyle atomik olarak değiştirilir.
 */
public final class ConfigFileEditor {

    private ConfigFileEditor() {}

    public static void setString(Path file, String key, String value, String commentIfAppended) throws IOException {
        if (!key.matches("^[a-z0-9-]+$")) throw new IllegalArgumentException("invalid key");
        List<String> lines = Files.exists(file)
                ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                : new ArrayList<>();
        Pattern keyLine = Pattern.compile("^" + Pattern.quote(key) + "\\s*:.*$");
        String newLine = key + ": " + quote(value);
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            if (keyLine.matcher(lines.get(i)).matches()) {
                if (!replaced) {
                    lines.set(i, newLine);
                    replaced = true;
                } else {
                    // Aynı anahtar iki kez yazılmışsa ikincisi eski değeri taşıyabilir: kaldır
                    lines.remove(i--);
                }
            }
        }
        if (!replaced) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) lines.add("");
            if (commentIfAppended != null) {
                for (String c : commentIfAppended.split("\n")) lines.add("# " + c);
            }
            lines.add(newLine);
        }
        SecureFiles.writeAtomically(file, String.join("\n", lines) + "\n");
    }

    /** YAML çift tırnaklı değer; kontrol karakterleri atılır */
    static String quote(String value) {
        String v = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "");
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
