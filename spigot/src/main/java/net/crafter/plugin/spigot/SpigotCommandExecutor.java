package net.crafter.plugin.spigot;

import net.crafter.plugin.core.CommandExecutor;
import net.crafter.plugin.core.model.PendingCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Spigot'ta komut çalıştırma implementasyonu.
 * Konsol üzerinden Bukkit.dispatchCommand() ile çalışır.
 */
public class SpigotCommandExecutor implements CommandExecutor {

    private final CrafterSpigotPlugin plugin;

    public SpigotCommandExecutor(CrafterSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(PendingCommand command) {
        String resolvedCmd = command.resolveCommand();
        if (resolvedCmd == null || resolvedCmd.isBlank()) {
            plugin.getLogger().warning("[Crafter] Received empty command ID: " + command.getCommandId());
            return false;
        }

        // Bukkit API main thread'de çalışmalı
        try {
            // Eğer zaten main thread'deyiz direkt çalıştır
            if (Bukkit.isPrimaryThread()) {
                return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCmd);
            }

            // Değilse scheduler ile main thread'e at ve bekle
            java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedCmd);
                    future.complete(result);
                } catch (Exception e) {
                    plugin.getLogger().severe("[Crafter] Command execution error: " + e.getMessage());
                    future.complete(false);
                }
            });

            // Max 5 saniye bekle
            return future.get(5, java.util.concurrent.TimeUnit.SECONDS);

        } catch (Exception e) {
            plugin.getLogger().severe("[Crafter] execute() method error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isPlayerOnline(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        return player != null && player.isOnline();
    }

    @Override
    public void sendMessage(String username, String message) {
        Player player = Bukkit.getPlayer(username);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    @Override
    public void execute(String command) {
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    @Override
    public void handlePurchaseResponse(String username, boolean success, String message) {
        Player player = Bukkit.getPlayer(username);
        if (player == null || !player.isOnline()) return;

        net.crafter.plugin.core.LanguageManager lang = plugin.getLanguageManager();

        if (success) {
            player.sendMessage(lang.getMessage("prefix") + lang.getMessage("purchase_success", message));
            
            net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(lang.getMessage("prefix") + lang.getMessage("click_to_view_chest"));
            
            net.md_5.bungee.api.chat.TextComponent viewChest = new net.md_5.bungee.api.chat.TextComponent(lang.getMessage("view_chest_button"));
            viewChest.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/crafter sandik"));
            viewChest.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.ComponentBuilder(lang.getMessage("view_chest_hover")).create()));
            
            msg.addExtra(viewChest);
            player.spigot().sendMessage(msg);
        } else {
            player.sendMessage(lang.getMessage("prefix") + lang.getMessage("purchase_error", message));
        }
    }

    @Override
    public String getPlatformName() {
        return "Spigot/" + Bukkit.getVersion();
    }
}
