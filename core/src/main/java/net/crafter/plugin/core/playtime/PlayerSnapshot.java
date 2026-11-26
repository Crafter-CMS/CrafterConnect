package net.crafter.plugin.core.playtime;

import java.util.UUID;

/**
 * Oyuncunun oyun içi istatistiği (#529): {@code Statistic.PLAY_ONE_MINUTE} tick'ten saniyeye çevrilmiş MUTLAK değer.
 * Oyun bu değeri oyuncu dosyasında tutar; çökme ya da yeniden başlatmada kaybolmaz (en çok son kayıttan beri olan
 * tick'ler geri gider).
 */
public record PlayerSnapshot(UUID uuid, String username, long playSeconds) {

    /** 20 tick = 1 saniye */
    public static long ticksToSeconds(long ticks) {
        return Math.max(0, ticks) / 20L;
    }
}
