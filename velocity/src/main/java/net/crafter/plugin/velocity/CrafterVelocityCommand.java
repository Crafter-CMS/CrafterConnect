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

        if (!source.hasPermission("crafter.admin")) {
            source.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return;
        }

        if (args.isEmpty() || args.equals("help")) {
            source.sendMessage(Component.text("[Crafter] Usage: /crafter <status|reload>", NamedTextColor.GOLD));
            return;
        }

        switch (args.toLowerCase()) {
            case "status" -> {
                boolean connected = plugin.getWsClient() != null && plugin.getWsClient().isConnected();
                source.sendMessage(Component.text(
                        "[Crafter] Status: " + (connected ? "CONNECTED" : "DISCONNECTED"),
                        connected ? NamedTextColor.GREEN : NamedTextColor.RED
                ));
                source.sendMessage(Component.text(
                        "[Crafter] Offline Queue: " + (plugin.getWsClient() != null
                                ? plugin.getWsClient().getOfflineQueueSize() : 0) + " commands",
                        NamedTextColor.YELLOW
                ));
            }
            default -> source.sendMessage(Component.text(
                    "[Crafter] Usage: /crafter <status>", NamedTextColor.GOLD));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("crafter.admin");
    }
}
