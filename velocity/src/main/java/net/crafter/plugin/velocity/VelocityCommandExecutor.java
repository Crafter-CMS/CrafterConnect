package net.crafter.plugin.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.crafter.plugin.core.CommandExecutor;
import net.crafter.plugin.core.model.PendingCommand;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Velocity'de komut çalıştırma implementasyonu.
 *
 * Velocity bir proxy olduğu için komutlar doğrudan backend sunucuda çalışmaz.
 * İki seçenek:
 *   1. Velocity'nin kendi komut dispatcher'ı (proxy komutları)
 *   2. Backend sunucuya PluginMessage ile iletmek
 *
 * Bu implementasyon Velocity komut dispatcher'ını kullanır.
 * Backend'e iletmek için ise her backend sunucuya da Spigot plugin'ini kurmanız gerekir
 * ve o sunucu da crafter.net.tr'ye bağlanır (önerilen mimari budur).
 *
 * Velocity plugin → sadece proxy-level komutlar için kullanılır
 * (örn: "velocity alert", "kick", vs.)
 * Spigot plugin → asıl item delivery için kullanılır
 */
public class VelocityCommandExecutor implements CommandExecutor {

    private final ProxyServer server;
    private final Logger logger;
    private final CrafterVelocityPlugin plugin;

    public VelocityCommandExecutor(ProxyServer server, Logger logger, CrafterVelocityPlugin plugin) {
        this.server = server;
        this.logger = logger;
        this.plugin = plugin;
    }

    @Override
    public boolean execute(PendingCommand command) {
        String resolvedCmd = command.resolveCommand();
        if (resolvedCmd == null || resolvedCmd.isBlank()) {
            logger.warn("[Crafter] Boş komut geldi: {}", command.getCommandId());
            return false;
        }

        try {
            // Velocity command manager ile çalıştır
            var future = server.getCommandManager().executeAsync(
                    server.getConsoleCommandSource(),
                    resolvedCmd
            );

            return future.get(5, TimeUnit.SECONDS);

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("[Crafter] Komut çalıştırma hatası: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isPlayerOnline(String playerName) {
        Optional<Player> player = server.getPlayer(playerName);
        return player.isPresent();
    }

    @Override
    public void sendMessage(String username, String message) {
        server.getPlayer(username).ifPresent(p -> p.sendMessage(net.kyori.adventure.text.Component.text(message)));
    }

    @Override
    public void execute(String command) {
        server.getCommandManager().executeAsync(server.getConsoleCommandSource(), command);
    }

    @Override
    public void handlePurchaseResponse(String username, boolean success, String message) {
        Optional<Player> playerOpt = server.getPlayer(username);
        if (playerOpt.isEmpty()) return;
        
        Player player = playerOpt.get();
        net.crafter.plugin.core.LanguageManager lm = plugin.getLanguageManager();
        if (success) {
            player.sendMessage(net.kyori.adventure.text.Component.text(lm.getMessage("prefix") + lm.getMessage("purchase_success", message)));
        } else {
            player.sendMessage(net.kyori.adventure.text.Component.text(lm.getMessage("prefix") + lm.getMessage("purchase_error", message)));
        }
    }

    @Override
    public String getPlatformName() {
        return "Velocity/" + server.getVersion().getVersion();
    }
}
