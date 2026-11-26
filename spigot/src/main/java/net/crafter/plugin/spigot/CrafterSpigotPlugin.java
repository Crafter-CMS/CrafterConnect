package net.crafter.plugin.spigot;

import net.crafter.plugin.core.AdminCommand;
import net.crafter.plugin.core.CrafterConnectService;
import net.crafter.plugin.core.LanguageManager;
import net.crafter.plugin.core.MarketManager;
import net.crafter.plugin.core.PlayerManager;
import net.crafter.plugin.core.StatisticsManager;
import net.crafter.plugin.core.playtime.PlayerSnapshot;
import net.crafter.plugin.spigot.menu.CategoryMenu;
import net.crafter.plugin.spigot.menu.ChestMenu;
import net.crafter.plugin.spigot.menu.MenuManager;
import net.crafter.plugin.spigot.menu.ShopMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CrafterConnect Paper/Spigot plugin (Folia compatible). Connection, queue and security live in the core
 * ({@link CrafterConnectService}); events, commands and in-game menus live here.
 */
public class CrafterSpigotPlugin extends JavaPlugin implements Listener {

    private CrafterConnectService service;
    private AdminCommand adminCommand;
    private SpigotScheduler scheduler;
    private MenuManager menuManager;
    private SpigotAfk afk;
    private final Set<String> pendingShopPlayers = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onEnable() {
        scheduler = new SpigotScheduler(this);
        SpigotPlatform platform = new SpigotPlatform(this, scheduler);
        afk = new SpigotAfk(platform.log());
        service = new CrafterConnectService(platform);
        adminCommand = new AdminCommand(service);
        service.marketManager().setOnUpdate(this::onMarketUpdate);
        service.marketManager().setOnChestUpdate((username, items) -> {
            Player p = Bukkit.getPlayerExact(username);
            if (p == null) return;
            scheduler.runForPlayer(p, () -> {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof ChestMenu chestMenu) {
                    chestMenu.refresh();
                }
            });
        });
        menuManager = new MenuManager();

        service.start();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(menuManager, this);
        getServer().getPluginManager().registerEvents(afk, this);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            if (new CrafterPlaceholderExpansion(this).register()) {
                getLogger().info("PlaceholderAPI expansion registered.");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion!");
            }
        }
        getLogger().info("CrafterConnect " + getDescription().getVersion() + " enabled"
                + (scheduler.isFolia() ? " (Folia)." : "."));
    }

    @Override
    public void onDisable() {
        if (service == null) return;
        // On shutdown players are kicked after plugins are disabled, so the final values are queued here
        if (!scheduler.isFolia()) {
            List<PlayerSnapshot> last = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    last.add(SpigotPlatform.snapshot(p));
                } catch (RuntimeException ignored) {
                    // a player that cannot be read is skipped; the sample from the last 5 minutes is already queued
                }
            }
            service.recordShutdown(last);
        }
        service.stop();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(AdminCommand.PERMISSION) && service.updateResult() != null && service.updateResult().hasUpdate()) {
            LanguageManager lang = getLanguageManager();
            player.sendMessage(lang.getMessage("prefix") + lang.getMessage("version_warning", service.updateResult().getLatestVersion()));
        }
        service.onPlayerJoin(player.getName(), player.getUniqueId());
        recordStats(player, "join");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        recordStats(event.getPlayer(), "quit");
        service.onPlayerQuit(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    /** Runs on the player's thread: stats are read here, sending happens on the io thread (#529) */
    private void recordStats(Player player, String reason) {
        try {
            service.recordPlayerStats(SpigotPlatform.snapshot(player), reason);
        } catch (RuntimeException e) {
            getLogger().fine("Could not read playtime statistic: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("crafter")) return false;
        LanguageManager lang = getLanguageManager();
        AdminCommand.Sender s = new AdminCommand.Sender() {
            @Override public boolean hasPermission(String permission) { return sender.hasPermission(permission); }
            @Override public void send(String message) {
                if (sender instanceof Player p) scheduler.runForPlayer(p, () -> p.sendMessage(message));
                else sender.sendMessage(message);
            }
        };
        if (args.length == 0) {
            sender.sendMessage(lang.getMessage("prefix") + lang.getMessage("usage"));
            return true;
        }
        if (adminCommand.handle(s, args)) return true;

        switch (args[0].toLowerCase()) {
            case "magaza", "shop" -> openShop(sender, args);
            case "sandik", "chest" -> {
                if (!(sender instanceof Player player)) return true;
                if (!player.hasPermission("crafter.chest")) {
                    player.sendMessage(lang.getMessage("prefix") + lang.getMessage("no_permission"));
                    return true;
                }
                player.sendMessage(lang.getMessage("prefix") + lang.getMessage("chest_opening"));
                service.getChest(player.getName(), player.getUniqueId());
                menuManager.openMenu(player, new ChestMenu(this, player).getInventory());
            }
            default -> sender.sendMessage(lang.getMessage("prefix") + lang.getMessage("usage"));
        }
        return true;
    }

    private void openShop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        LanguageManager lang = getLanguageManager();
        if (!player.hasPermission("crafter.shop")) {
            player.sendMessage(lang.getMessage("prefix") + lang.getMessage("no_permission"));
            return;
        }
        MarketManager market = service.marketManager();
        if (!market.isLoaded()) {
            pendingShopPlayers.add(player.getName());
            player.sendMessage(lang.getMessage("prefix") + lang.getMessage("shop_loading"));
            service.getProducts();
            return;
        }
        if (args.length > 1) {
            String catId = args[1];
            if (market.getCategories().stream().anyMatch(c -> c.getId().equalsIgnoreCase(catId))) {
                menuManager.openMenu(player, new ShopMenu(this, catId).getInventory());
            } else {
                player.sendMessage(lang.getMessage("prefix") + lang.getMessage("category_not_found", catId));
            }
        } else {
            menuManager.openMenu(player, new CategoryMenu(this).getInventory());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return AdminCommand.complete(args, sender.hasPermission(AdminCommand.PERMISSION), List.of("magaza", "sandik"));
    }

    private void onMarketUpdate() {
        for (String playerName : List.copyOf(pendingShopPlayers)) {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player != null) {
                scheduler.runForPlayer(player, () -> menuManager.openMenu(player, new CategoryMenu(this).getInventory()));
            }
        }
        pendingShopPlayers.clear();
    }

    // --- For menus and PlaceholderAPI ---

    public CrafterConnectService getService() { return service; }
    SpigotAfk afk() { return afk; }
    public LanguageManager getLanguageManager() { return service.lang(); }
    public StatisticsManager getStatsManager() { return service.statsManager(); }
    public PlayerManager getPlayerManager() { return service.playerManager(); }
    public MarketManager getMarketManager() { return service.marketManager(); }
}
