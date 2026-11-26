package net.crafter.plugin.spigot;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Paper/Spigot ve Folia için zamanlayıcı. Folia'da Bukkit zamanlayıcısı yoktur: konsol komutları global bölge
 * zamanlayıcısında, oyuncu işlemleri (menü açma) oyuncunun kendi zamanlayıcısında çalışır. Folia API'si
 * derleme bağımlılığı olmadan yansıma ile çağrılır.
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

    /** Ana thread'de (Folia'da global bölgede) çalıştırır; geliş sırası korunur */
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

    /** Oyuncuya ait işlem (envanter açma vb.); oyuncu çıktıysa çalışmaz */
    public void runForPlayer(Player player, Runnable task) {
        runForPlayer(player, task, null);
    }

    /**
     * Oyuncuya ait işlem; Folia'da oyuncu çıkmışsa {@code retired} çalışır (null olabilir). Paper/Spigot'ta ana
     * thread'de çalışır.
     */
    public void runForPlayer(Player player, Runnable task, Runnable retired) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        try {
            Consumer<Object> consumer = ignored -> task.run();
            Object scheduled = entityRun.invoke(entityScheduler.invoke(player), plugin, consumer, retired);
            // run() null dönerse varlık zaten kaldırılmıştır
            if (scheduled == null && retired != null) retired.run();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
