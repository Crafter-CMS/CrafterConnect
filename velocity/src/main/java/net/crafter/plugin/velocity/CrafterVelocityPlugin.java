package net.crafter.plugin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.crafter.plugin.core.AdminCommand;
import net.crafter.plugin.core.CrafterConnectService;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * CrafterConnect Velocity plugin. The proxy connects as a separate CrafterConnect server; delivery commands
 * run on the proxy console. player_join/player_left are sent once from the proxy (with
 * {@code report-players: false} on backend servers).
 */
@Plugin(
        id = "crafter-connect",
        name = "CrafterConnect",
        version = "2.0.0",
        description = "Crafter store delivery for Velocity",
        url = "https://crafter.net.tr",
        authors = {"Crafter"},
        dependencies = {@Dependency(id = "floodgate", optional = true)}
)
public class CrafterVelocityPlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private VelocityPlatform platform;
    private CrafterConnectService service;

    @Inject
    public CrafterVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        platform = new VelocityPlatform(server, logger, dataDirectory, () -> service != null && service.config().isDebug());
        service = new CrafterConnectService(platform);
        service.start();
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("crafter").plugin(this).build(),
                new CrafterVelocityCommand(service));
        logger.info("CrafterConnect enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (service != null) service.stop();
        if (platform != null) platform.shutdown();
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        var player = event.getPlayer();
        if (player.hasPermission(AdminCommand.PERMISSION) && service.updateResult() != null && service.updateResult().hasUpdate()) {
            player.sendMessage(VelocityPlatform.legacy(service.lang().getMessage("prefix")
                    + service.lang().getMessage("version_warning", service.updateResult().getLatestVersion())));
        }
        service.onPlayerJoin(player.getUsername(), player.getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        service.onPlayerQuit(event.getPlayer().getUsername(), event.getPlayer().getUniqueId());
    }
}
