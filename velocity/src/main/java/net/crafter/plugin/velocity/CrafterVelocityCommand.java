package net.crafter.plugin.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import net.crafter.plugin.core.AdminCommand;
import net.crafter.plugin.core.CrafterConnectService;

import java.util.List;

/** /crafter (Velocity): connect, disconnect, status, reload */
public class CrafterVelocityCommand implements SimpleCommand {

    private final CrafterConnectService service;
    private final AdminCommand admin;

    public CrafterVelocityCommand(CrafterConnectService service) {
        this.service = service;
        this.admin = new AdminCommand(service);
    }

    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        AdminCommand.Sender sender = new AdminCommand.Sender() {
            @Override public boolean hasPermission(String permission) { return source.hasPermission(permission); }
            @Override public void send(String message) { source.sendMessage(VelocityPlatform.legacy(message)); }
        };
        if (!admin.handle(sender, invocation.arguments())) {
            sender.send(service.lang().getMessage("prefix") + service.lang().getMessage("usage_proxy"));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        return AdminCommand.complete(args.length == 0 ? new String[]{""} : args,
                invocation.source().hasPermission(AdminCommand.PERMISSION), List.of());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(AdminCommand.PERMISSION);
    }
}
