package net.crafter.plugin.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.crafter.plugin.core.model.PendingCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Oyuncu çevrimdışıyken bekletilen komutların ve çalıştırılmış komut id'lerinin kalıcı deposu (#273).
 *
 * - Bekleyen komutlar oyuncu adının küçük harfli hâliyle tutulur; sunucu yeniden başlasa da kaybolmaz.
 * - Çalıştırılan komut id'leri saklanır; backend aynı komutu tekrar gönderirse ikinci kez çalıştırılmaz.
 * - Dosya yolu null ise yalnızca bellekte çalışır (testler ve eski kurulum).
 */
public class PendingCommandStore {

    private static final Logger log = LoggerFactory.getLogger(PendingCommandStore.class);
    /** Saklanan çalıştırılmış id sayısı; eskiler atılır */
    static final int MAX_EXECUTED_IDS = 5000;

    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, List<PendingCommand>> queue = new LinkedHashMap<>();
    private final LinkedHashSet<String> executedIds = new LinkedHashSet<>();

    public PendingCommandStore(Path file) {
        this.file = file;
        load();
    }

    /** Oyuncu adını eşleme anahtarına çevirir (büyük/küçük harf duyarsız). */
    public static String playerKey(String player) {
        return player == null ? "" : player.trim().toLowerCase(Locale.ROOT);
    }

    public synchronized void enqueue(String player, PendingCommand command) {
        queue.computeIfAbsent(playerKey(player), k -> new ArrayList<>()).add(command);
        save();
    }

    /** Oyuncunun bekleyen komutlarını sırayla verir ve kuyruktan siler. */
    public synchronized List<PendingCommand> drain(String player) {
        List<PendingCommand> commands = queue.remove(playerKey(player));
        if (commands == null) return Collections.emptyList();
        save();
        return commands;
    }

    public synchronized boolean isQueued(String commandId) {
        if (commandId == null) return false;
        for (List<PendingCommand> commands : queue.values()) {
            for (PendingCommand command : commands) {
                if (commandId.equals(command.getCommandId())) return true;
            }
        }
        return false;
    }

    public synchronized boolean isExecuted(String commandId) {
        return commandId != null && executedIds.contains(commandId);
    }

    public synchronized void markExecuted(String commandId) {
        if (commandId == null) return;
        executedIds.add(commandId);
        Iterator<String> it = executedIds.iterator();
        while (executedIds.size() > MAX_EXECUTED_IDS && it.hasNext()) {
            it.next();
            it.remove();
        }
        save();
    }

    public synchronized int size() {
        return queue.values().stream().mapToInt(List::size).sum();
    }

    private void load() {
        if (file == null || !Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Snapshot snapshot = gson.fromJson(reader, new TypeToken<Snapshot>() {}.getType());
            if (snapshot == null) return;
            if (snapshot.queue != null) {
                // Eski sürümlerde anahtar farklı yazılmış olabilir; hepsini küçük harfe birleştir
                snapshot.queue.forEach((player, commands) -> {
                    if (commands != null) queue.computeIfAbsent(playerKey(player), k -> new ArrayList<>()).addAll(commands);
                });
            }
            if (snapshot.executedIds != null) executedIds.addAll(snapshot.executedIds);
        } catch (Exception e) {
            log.error("[CrafterConnect] Could not read pending command file: {}", e.getMessage());
        }
    }

    private void save() {
        if (file == null) return;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Snapshot snapshot = new Snapshot();
            snapshot.queue = queue;
            snapshot.executedIds = new ArrayList<>(executedIds);
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(snapshot, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("[CrafterConnect] Could not write pending command file: {}", e.getMessage());
        }
    }

    private static class Snapshot {
        Map<String, List<PendingCommand>> queue;
        List<String> executedIds;
    }
}
