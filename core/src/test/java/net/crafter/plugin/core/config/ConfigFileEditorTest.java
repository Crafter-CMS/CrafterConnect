package net.crafter.plugin.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFileEditorTest {

    @TempDir Path dir;

    @Test
    void replacesValueKeepsCommentsAndRestrictsPermissions() throws Exception {
        Path cfg = dir.resolve("config.yml");
        Files.writeString(cfg, "# yorum\napi-url: \"api.crafter.net.tr\"\nserver-token: \"\"\n  nested-server-token: x\nlanguage: \"tr\"\n");
        Files.setPosixFilePermissions(cfg, PosixFilePermissions.fromString("rw-r--r--"));
        assertTrue(SecureFiles.isReadableByOthers(cfg));

        ConfigFileEditor.setString(cfg, "server-token", "cct_abc", "c");
        String text = Files.readString(cfg);
        assertTrue(text.contains("# yorum"));
        assertTrue(text.contains("server-token: \"cct_abc\""));
        assertTrue(text.contains("  nested-server-token: x"));
        assertTrue(text.contains("language: \"tr\""));
        assertFalse(text.contains("# c"));
        if (Files.getFileAttributeView(cfg, PosixFileAttributeView.class) != null) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(cfg)));
            assertFalse(SecureFiles.isReadableByOthers(cfg));
        }
    }

    @Test
    void appendsMissingKeyWithCommentAndRemovesDuplicates() throws Exception {
        Path cfg = dir.resolve("config.yml");
        Files.writeString(cfg, "website-id: \"w\"\n");
        ConfigFileEditor.setString(cfg, "server-token", "cct_1", "line1\nline2");
        assertTrue(Files.readString(cfg).endsWith("# line1\n# line2\nserver-token: \"cct_1\"\n"));

        Files.writeString(cfg, "server-token: \"a\"\nserver-token: \"old\"\n");
        ConfigFileEditor.setString(cfg, "server-token", "", null);
        assertEquals("server-token: \"\"\n", Files.readString(cfg));
    }

    @Test
    void quotesAndStripsControlCharacters() {
        assertEquals("\"a\\\"b\\\\c\"", ConfigFileEditor.quote("a\"b\\c\n"));
    }
}
