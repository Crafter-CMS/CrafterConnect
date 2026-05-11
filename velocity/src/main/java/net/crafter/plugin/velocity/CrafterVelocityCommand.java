package net.crafter.plugin.velocity;

import com.velocitypowered.api.command.RawCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * /crafter komutu — Velocity versiyonu
 */
public class CrafterVelocityCommand implements RawCommand {

    private final CrafterVelocityPlugin plugin;

    public CrafterVelocityCommand(CrafterVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String args = invocation.arguments().trim();

        net.crafter.plugin.core.LanguageManager lm = plugin.getLanguageManager();
        if (lm == null) return;

        if (!source.hasPermission("crafter.admin")) {
            source.sendMessage(Component.text(lm.getMessage("prefix") + lm.getMessage("no_permission")));
            return;
        }

        if (args.isEmpty() || args.equals("help")) {
            source.sendMessage(Component.text(lm.getMessage("prefix") + lm.getMessage("usage")));
            return;
        }

        switch (args.toLowerCase()) {
            case "status" -> {
                boolean connected = plugin.getWsClient() != null && plugin.getWsClient().isConnected();
                source.sendMessage(Component.text(lm.getMessage("prefix") + lm.getMessage("status_title")));
                source.sendMessage(Component.text(lm.getMessage("status_info_line", (connected ? lm.getMessage("status_connected") : lm.getMessage("status_disconnected")))));
                source.sendMessage(Component.text(lm.getMessage("status_queue_line", (plugin.getWsClient() != null ? plugin.getWsClient().getOfflineQueueSize() : 0))));
            }
            case "reload" -> {
                try {
                    plugin.reload();
                    source.sendMessage(Component.text(lm.getMessage("prefix") + lm.getMessage("reloaded")));
                } catch (Exception e) {
                    source.sendMessage(Component.text(lm.getMessage("prefix") + lm.getMessage("reload_error", e.getMessage())));
                }
            }
            default -> source.sendMessage(Component.text(lm.getMessage("prefix") + lm.getMessage("usage")));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("crafter.admin");
    }
}
