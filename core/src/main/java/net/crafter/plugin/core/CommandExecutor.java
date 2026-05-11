package net.crafter.plugin.core;

import net.crafter.plugin.core.model.PendingCommand;

/**
 * Platform-bağımsız komut çalıştırıcı arayüzü.
 */
public interface CommandExecutor {

    /**
     * Komutu ilgili platformda çalıştırır.
     * @param command Çalıştırılacak komut paketi
     * @return true → başarılı
     */
    boolean execute(PendingCommand command);

    /**
     * Konsol üzerinden doğrudan komut çalıştırır.
     */
    void execute(String command);

    /**
     * Oyuncuya mesaj gönderir.
     */
    void sendMessage(String username, String message);

    /**
     * Satın alma sonucunu işler.
     */
    void handlePurchaseResponse(String username, boolean success, String message);

    /**
     * Oyuncunun online olup olmadığını kontrol eder.
     */
    boolean isPlayerOnline(String playerName);

    /**
     * Platform adı.
     */
    String getPlatformName();
}
