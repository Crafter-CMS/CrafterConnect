package net.crafter.plugin.spigot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Scheduler for Paper/Spigot and Folia. Folia has no Bukkit scheduler: console commands run on the global region
 * scheduler and player actions (opening menus) on the player's own scheduler. The Folia API is called via
 * reflection without a compile dependency.
 */
public final class SpigotScheduler {

    private final Plugin plugin;
    private final boolean folia;
    private Method globalScheduler;   // Server#getGlobalRegionScheduler()
    private Method globalExecute;     // GlobalRegionScheduler#execute(Plugin, Runnable)
    private Method entityScheduler;   // Entity#getScheduler()
    private Method entityRun;         // EntityScheduler#run(Plugin, Consumer, Runnable)

    public SpigotScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
        if (folia) {
            try {
                globalScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
                Class<?> grs = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                globalExecute = grs.getMethod("execute", Plugin.class, Runnable.class);
                entityScheduler = Class.forName("org.bukkit.entity.Entity").getMethod("getScheduler");
                Class<?> es = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                entityRun = es.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Folia scheduler API not found", e);
            }
        }
    }

    public static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isFolia() {
        return folia;
    }

    /** Runs on the main thread (the global region on Folia); arrival order is preserved */
    public void runGlobal(Runnable task) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        try {
            globalExecute.invoke(globalScheduler.invoke(Bukkit.getServer()), plugin, task);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Player-bound action (opening an inventory, etc.); does not run if the player has left */
    public void runForPlayer(Player player, Runnable task) {
        runForPlayer(player, task, null);
    }

    /**
     * Player-bound action; on Folia {@code retired} runs if the player has left (may be null). On Paper/Spigot it runs
     * on the main thread.
     */
    public void runForPlayer(Player player, Runnable task, Runnable retired) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        try {
            Consumer<Object> consumer = ignored -> task.run();
            Object scheduled = entityRun.invoke(entityScheduler.invoke(player), plugin, consumer, retired);
            // if run() returns null the entity has already been removed
            if (scheduled == null && retired != null) retired.run();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
