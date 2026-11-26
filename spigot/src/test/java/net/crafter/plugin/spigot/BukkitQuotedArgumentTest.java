package net.crafter.plugin.spigot;

import net.crafter.plugin.core.delivery.CommandGuard;
import net.crafter.plugin.core.model.PendingCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boşluklu Bedrock/Floodgate adının (backend #247: çift tırnak içinde tek argüman) Bukkit komut ayrıştırmasındaki
 * davranışı. Spigot API'sindeki gerçek {@link SimpleCommandMap} ile denenir (sunucu yok).
 *
 * Sonuç: Bukkit argümanları yalnızca boşluktan böler, tırnakları yorumlamaz. Tırnaklı ad komuta iki argüman olarak
 * ({@code "".Cool"}, {@code "Gamer""}) ulaşır; birleştirme hedef eklentiye kalır (LuckPerms tırnaklı argümanı tek
 * argüman olarak okur). Brigadier/vanilla komutlarında ({@code give}) oyuncu argümanı tırnak kabul etmez.
 */
class BukkitQuotedArgumentTest {

    static final class Capture extends Command {
        final List<String[]> calls = new ArrayList<>();
        Capture(String name) { super(name); }
        @Override public boolean execute(CommandSender sender, String label, String[] args) {
            calls.add(args);
            return true;
        }
    }

    private static CommandSender console() {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasPermission", "isOp" -> true;
                    case "getName" -> "CONSOLE";
                    default -> null;
                });
    }

    /** Sahte Server: yalnızca dispatch'in kullandığı çağrılar (PluginManager.useTimings vb.) */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> r = method.getReturnType();
            if (r == boolean.class) return false;
            if (r == int.class || r == long.class) return 0;
            if (r == String.class) return "test";
            if (r == java.util.logging.Logger.class) return java.util.logging.Logger.getLogger("test");
            if (r == org.bukkit.plugin.PluginManager.class) return stub(org.bukkit.plugin.PluginManager.class);
            return null;
        });
    }

    @Test
    void bukkitSplitsQuotedNameOnSpaces() {
        if (org.bukkit.Bukkit.getServer() == null) org.bukkit.Bukkit.setServer(stub(org.bukkit.Server.class));
        SimpleCommandMap map = new SimpleCommandMap(null);
        Capture lp = new Capture("lp");
        map.register("test", lp);

        PendingCommand pc = new PendingCommand("cmd_1", "lp user %player% parent add vip", ".Cool Gamer");
        assertNull(new CommandGuard(null, "minecraft_java", List.of(), List.of()).check(pc));
        String resolved = pc.resolveCommand();
        assertEquals("lp user \".Cool Gamer\" parent add vip", resolved);

        assertTrue(map.dispatch(console(), resolved));
        assertArrayEquals(new String[]{"user", "\".Cool", "Gamer\"", "parent", "add", "vip"}, lp.calls.get(0));

        // Java adı: tırnak yok, tek argüman
        assertTrue(map.dispatch(console(), new PendingCommand("cmd_2", "lp user %player% parent add vip", "Notch").resolveCommand()));
        assertArrayEquals(new String[]{"user", "Notch", "parent", "add", "vip"}, lp.calls.get(1));
    }
}
