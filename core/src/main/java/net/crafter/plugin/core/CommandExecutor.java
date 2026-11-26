package net.crafter.plugin.core;

import java.util.function.Consumer;

/**
 * Platforma özgü komut çalıştırma ve oyuncu işlemleri.
 */
public interface CommandExecutor {

    /**
     * Komutu konsol göndericisiyle platformun ana thread'inde (Folia'da global bölge zamanlayıcısında, proxy'de
     * sıralı bir yürütücüde) çalıştırır. Çağıran thread'i bekletmez; geliş sırası korunur.
     *
     * @param command  baştaki "/" olmadan, doğrulanmış komut
     * @param callback sonuç (true: komut çalıştı); herhangi bir thread'den çağrılabilir
     */
    void dispatchConsole(String command, Consumer<Boolean> callback);

    /** Oyuncu çevrimiçi mi (ad büyük/küçük harf duyarsız) */
    boolean isPlayerOnline(String playerName);

    /** Bu UUID'li oyuncu çevrimiçi mi */
    default boolean isPlayerOnline(java.util.UUID uuid) {
        return false;
    }

    void sendMessage(String username, String message);

    void handlePurchaseResponse(String username, boolean success, String message);

    /** Konsolda gösterilecek platform adı ve sürümü */
    String getPlatformName();
}
