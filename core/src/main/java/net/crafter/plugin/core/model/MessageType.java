package net.crafter.plugin.core.model;

/**
 * Her WebSocket mesajının taşıdığı tip bilgisi.
 * Sunucu (crafter.net.tr) → Plugin yönünde gelen mesaj tipleri.
 */
public enum MessageType {
    // crafter.net.tr → Plugin
    AUTH_OK,          // Kimlik doğrulama başarılı
    AUTH_FAIL,        // Kimlik doğrulama başarısız
    COMMAND,          // Çalıştırılacak komut
    STATISTICS,       // İstatistik verileri
    PING,             // Canlılık kontrolü

    // Plugin → crafter.net.tr
    AUTHENTICATE,     // İlk bağlantıda token gönder
    ACK,              // Komut başarıyla çalıştırıldı
    NACK,             // Komut çalıştırılamadı (oyuncu offline vs.)
    PONG              // Ping'e cevap
}
