package net.crafter.plugin.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Replaces a single top-level value in config.yml line by line; comments and other settings are kept.
 * Saving with Bukkit/Configurate can drop comments, so {@code /crafter connect} writes the token with this class.
 * The file is replaced atomically with 600 permissions on every write.
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
                    // If the same key was written twice, the second may carry the old value: remove it
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

    /** YAML double-quoted value; control characters are dropped */
    static String quote(String value) {
        String v = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", "");
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
