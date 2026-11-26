package net.crafter.plugin.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code /crafter connect <kod> | disconnect | status | reload} (yetki {@code crafter.admin}, konsoldan da çalışır).
 * Platformlar komutu buraya iletir; {@code shop} ve {@code chest} Paper/Spigot modülünde kalır.
 */
public final class AdminCommand {

    public static final String PERMISSION = "crafter.admin";
    public static final List<String> SUBCOMMANDS = List.of("connect", "disconnect", "status", "reload");

    /** Komutu yazan (oyuncu ya da konsol) */
    public interface Sender {
        boolean hasPermission(String permission);
        void send(String message);
    }

    private final CrafterConnectService service;

    public AdminCommand(CrafterConnectService service) {
        this.service = service;
    }

    /** @return komut bu sınıfta işlendiyse true */
    public boolean handle(Sender sender, String[] args) {
        if (args.length == 0) return false;
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) return false;
        LanguageManager lang = service.lang();
        if (!sender.hasPermission(PERMISSION)) {
            sender.send(lang.getMessage("prefix") + lang.getMessage("no_permission"));
            return true;
        }
        Consumer<String> reply = sender::send;
        switch (sub) {
            case "connect" -> {
                if (args.length < 2) {
                    sender.send(lang.getMessage("prefix") + lang.getMessage("connect_usage"));
                } else {
                    // "K7QM 4XPD" gibi boşluklu yazım da kabul edilir
                    service.connect(String.join("", java.util.Arrays.copyOfRange(args, 1, args.length)), reply);
                }
            }
            case "disconnect" -> service.disconnect(reply);
            case "status" -> service.statusLines().forEach(sender::send);
            case "reload" -> {
                try {
                    service.reload();
                    sender.send(service.lang().getMessage("prefix") + service.lang().getMessage("reloaded"));
                } catch (RuntimeException e) {
                    sender.send(lang.getMessage("prefix") + lang.getMessage("reload_error", e.getMessage()));
                }
            }
            default -> { return false; }
        }
        return true;
    }

    public static List<String> complete(String[] args, boolean admin, List<String> extra) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String start = args[0].toLowerCase(Locale.ROOT);
            List<String> all = new ArrayList<>(extra);
            if (admin) all.addAll(SUBCOMMANDS);
            for (String s : all) if (s.startsWith(start)) out.add(s);
        }
        return out;
    }
}
