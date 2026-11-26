package net.crafter.plugin.spigot;

import net.crafter.plugin.core.playtime.AfkSource;
import net.crafter.plugin.core.playtime.IdleAfkSource;
import net.crafter.plugin.core.platform.CrafterLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * AFK kaynağı (#529). {@code auto}: Essentials, yoksa CMI, yoksa hareketsizlik eşiği. Essentials ve CMI derleme
 * bağımlılığı olmadan yansıma ile okunur (yalnızca okuma; AFK durumu değiştirilmez). Hareketsizlik için
 * hareket/bakış, sohbet, komut ve etkileşim olayları dinlenir.
 */
public final class SpigotAfk implements Listener {

    private final CrafterLog log;
    private volatile IdleAfkSource idle;

    public SpigotAfk(CrafterLog log) {
        this.log = log;
    }

    /** @param mode auto | essentials | cmi | idle | none */
    public AfkSource select(String mode, long idleThresholdMs) {
        idle = null;
        if ("none".equals(mode)) return AfkSource.NONE;
        if ("auto".equals(mode) || "essentials".equals(mode)) {
            AfkSource ess = essentials();
            if (ess != null) {
                log.info("[CrafterConnect] AFK time from Essentials.");
                return ess;
            }
        }
        if ("auto".equals(mode) || "cmi".equals(mode)) {
            AfkSource cmi = cmi();
            if (cmi != null) {
                log.info("[CrafterConnect] AFK time from CMI.");
                return cmi;
            }
        }
        if (!"auto".equals(mode) && !"idle".equals(mode)) {
            log.warn("[CrafterConnect] playtime.afk-source: " + mode + " not found; using the idle threshold.");
        }
        IdleAfkSource src = new IdleAfkSource(idleThresholdMs);
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) src.markActive(p.getUniqueId(), now);
        idle = src;
        return src;
    }

    private static AfkSource essentials() {
        Plugin ess = Bukkit.getPluginManager().getPlugin("Essentials");
        if (ess == null || !ess.isEnabled()) return null;
        try {
            Method getUser = ess.getClass().getMethod("getUser", Player.class);
            Method isAfk = getUser.getReturnType().getMethod("isAfk");
            return (uuid, now) -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) return false;
                try {
                    Object user = getUser.invoke(ess, p);
                    return user != null && Boolean.TRUE.equals(isAfk.invoke(user));
                } catch (ReflectiveOperationException e) {
                    return false;
                }
            };
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static AfkSource cmi() {
        Plugin cmiPlugin = Bukkit.getPluginManager().getPlugin("CMI");
        if (cmiPlugin == null || !cmiPlugin.isEnabled()) return null;
        try {
            Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI", true, cmiPlugin.getClass().getClassLoader());
            Object cmi = cmiClass.getMethod("getInstance").invoke(null);
            Object playerManager = cmiClass.getMethod("getPlayerManager").invoke(cmi);
            Method getUser = playerManager.getClass().getMethod("getUser", Player.class);
            Method isAfk = getUser.getReturnType().getMethod("isAfk");
            return (uuid, now) -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) return false;
                try {
                    Object user = getUser.invoke(playerManager, p);
                    return user != null && Boolean.TRUE.equals(isAfk.invoke(user));
                } catch (ReflectiveOperationException e) {
                    return false;
                }
            };
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private void active(Player player) {
        IdleAfkSource src = idle;
        if (src != null) src.markActive(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (idle == null) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Yalnızca gerçek hareket ya da bakış değişimi (su akıntısı/itilme de hareket sayılır; bakış daha güvenilir)
        if (from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()
                || from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncPlayerChatEvent event) { active(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) { active(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) { active(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { active(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        IdleAfkSource src = idle;
        if (src != null) src.forget(event.getPlayer().getUniqueId());
    }
}
