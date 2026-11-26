package net.crafter.plugin.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Gizli bilgi taşıyan dosyalar (config.yml içindeki server-token, data/ klasörü) için izin ve atomik yazma.
 * POSIX dosya sistemlerinde dosya 600, klasör 700 yapılır; Windows'ta izin değiştirilmez (sunucu kullanıcısının
 * klasörü varsayılır).
 */
public final class SecureFiles {

    private static final Set<PosixFilePermission> FILE_PERMS = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> DIR_PERMS = PosixFilePermissions.fromString("rwx------");

    private SecureFiles() {}

    /** Dosyayı yalnızca sahibinin okuyup yazabileceği hale getirir. Başarılıysa ya da POSIX değilse true. */
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
            // izin değiştirilemiyorsa klasör yine kullanılır
        }
    }

    /** Grup ya da diğer kullanıcılar dosyayı okuyabiliyor mu (POSIX değilse false) */
    public static boolean isReadableByOthers(Path file) {
        try {
            if (!Files.exists(file) || Files.getFileAttributeView(file, PosixFileAttributeView.class) == null) return false;
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            return perms.contains(PosixFilePermission.GROUP_READ) || perms.contains(PosixFilePermission.OTHERS_READ);
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    /** İçeriği aynı klasörde geçici dosyaya yazıp yerine taşır; dosya 600 olur. */
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
