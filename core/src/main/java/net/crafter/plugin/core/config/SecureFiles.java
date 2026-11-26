package net.crafter.plugin.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Permissions and atomic writes for files holding secrets (server-token in config.yml, the data/ folder).
 * On POSIX file systems files become 600 and folders 700; on Windows permissions are left unchanged (the server
 * user's folder is assumed).
 */
public final class SecureFiles {

    private static final Set<PosixFilePermission> FILE_PERMS = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> DIR_PERMS = PosixFilePermissions.fromString("rwx------");

    private SecureFiles() {}

    /** Makes the file readable and writable by its owner only. True on success or if not POSIX. */
    public static boolean restrictFile(Path file) {
        try {
            if (!Files.exists(file) || Files.getFileAttributeView(file, PosixFileAttributeView.class) == null) return true;
            Files.setPosixFilePermissions(file, FILE_PERMS);
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    public static void ensurePrivateDirectory(Path dir) throws IOException {
        Files.createDirectories(dir);
        try {
            if (Files.getFileAttributeView(dir, PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(dir, DIR_PERMS);
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // if permissions cannot be changed, the folder is still used
        }
    }

    /** Whether group or other users can read the file (false if not POSIX) */
    public static boolean isReadableByOthers(Path file) {
        try {
            if (!Files.exists(file) || Files.getFileAttributeView(file, PosixFileAttributeView.class) == null) return false;
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            return perms.contains(PosixFilePermission.GROUP_READ) || perms.contains(PosixFilePermission.OTHERS_READ);
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    /** Writes the content to a temp file in the same folder and moves it into place; the file becomes 600. */
    public static void writeAtomically(Path file, String content) throws IOException {
        Path abs = file.toAbsolutePath();
        Path parent = abs.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        restrictFile(tmp);
        try {
            Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
        }
        restrictFile(abs);
    }
}
