package net.crafter.plugin.bungee;

import net.crafter.plugin.core.AdminCommand;
import net.crafter.plugin.core.CrafterConnectService;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.event.EventHandler;

import java.util.List;

/**
 * CrafterConnect BungeeCord / Waterfall eklentisi (#371). Velocity modülüyle aynı davranır: proxy ayrı bir
 * CrafterConnect sunucusu olarak bağlanır, teslimat komutları proxy konsolunda çalışır, oyuncu giriş/çıkışı
 * proxy'den bir kez bildirilir.
 */
public class CrafterBungeePlugin extends Plugin implements Listener {

    private BungeePlatform platform;
    private CrafterConnectService service;

    @Override
    public void onEnable() {
        platform = new BungeePlatform(this);
        service = new CrafterConnectService(platform);
        service.start();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new CrafterCommand());
        getLogger().info("CrafterConnect enabled.");
    }

    @Override
    public void onDisable() {
        if (service != null) service.stop();
        if (platform != null) platform.shutdown();
    }

    public CrafterConnectService getService() {
        return service;
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (player.hasPermission(AdminCommand.PERMISSION) && service.updateResult() != null && service.updateResult().hasUpdate()) {
            player.sendMessage(TextComponent.fromLegacyText(service.lang().getMessage("prefix")
                    + service.lang().getMessage("version_warning", service.updateResult().getLatestVersion())));
        }
        service.onPlayerJoin(player.getName(), player.getUniqueId());
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        service.onPlayerQuit(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    private final class CrafterCommand extends Command implements TabExecutor {
        private final AdminCommand admin = new AdminCommand(service);

        CrafterCommand() {
            super("crafter", AdminCommand.PERMISSION);
        }

        @Override
        public void execute(CommandSender source, String[] args) {
            AdminCommand.Sender sender = new AdminCommand.Sender() {
                @Override public boolean hasPermission(String permission) { return source.hasPermission(permission); }
                @Override public void send(String message) { source.sendMessage(TextComponent.fromLegacyText(message)); }
            };
            if (!admin.handle(sender, args)) {
                sender.send(service.lang().getMessage("prefix") + service.lang().getMessage("usage_proxy"));
            }
        }

        @Override
        public Iterable<String> onTabComplete(CommandSender source, String[] args) {
            return AdminCommand.complete(args, source.hasPermission(AdminCommand.PERMISSION), List.of());
        }
    }
}
