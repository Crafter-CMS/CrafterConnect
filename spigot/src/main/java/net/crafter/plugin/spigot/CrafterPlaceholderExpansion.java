package net.crafter.plugin.spigot;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.crafter.plugin.core.model.StatisticsData;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CrafterPlaceholderExpansion extends PlaceholderExpansion {

    private final CrafterSpigotPlugin plugin;

    public CrafterPlaceholderExpansion(CrafterSpigotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "crafterconnect";
    }

    @Override
    public @NotNull String getAuthor() {
        return "crafter.net.tr";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.5";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        switch (params.toLowerCase()) {
            case "total_users" -> {
                StatisticsData stats = plugin.getStatsManager().getStats();
                return stats != null ? String.valueOf(stats.getTotalUsers()) : "0";
            }
            case "last_purchase_user" -> {
                StatisticsData stats = plugin.getStatsManager().getStats();
                if (stats == null || stats.getLatest() == null) return "-";
                List<StatisticsData.Purchase> purchases = stats.getLatest().getPurchases();
                return (purchases != null && !purchases.isEmpty()) ? purchases.get(0).getUsername() : "-";
            }
            case "last_purchase_product" -> {
                StatisticsData stats = plugin.getStatsManager().getStats();
                if (stats == null || stats.getLatest() == null) return "-";
                List<StatisticsData.Purchase> purchases = stats.getLatest().getPurchases();
                return (purchases != null && !purchases.isEmpty()) ? purchases.get(0).getProductName() : "-";
            }
            case "top_loader_user" -> {
                StatisticsData stats = plugin.getStatsManager().getStats();
                if (stats == null) return "-";
                List<StatisticsData.TopLoader> top = stats.getTopCreditLoaders();
                return (top != null && !top.isEmpty()) ? top.get(0).getUsername() : "-";
            }
            case "top_loader_amount" -> {
                StatisticsData stats = plugin.getStatsManager().getStats();
                if (stats == null) return "0";
                List<StatisticsData.TopLoader> top = stats.getTopCreditLoaders();
                return (top != null && !top.isEmpty()) ? String.valueOf(top.get(0).getTotalAmount()) : "0";
            }
            case "balance" -> {
                if (player == null) return "0.00";
                net.crafter.plugin.core.model.PlayerData data = plugin.getPlayerManager().getPlayerData(player.getName());
                String amount = (data != null) ? String.format("%.2f", data.getBalance()) : "0.00";
                return plugin.getLanguageManager().getMessage("placeholder_balance_format", amount);
            }
            case "role" -> {
                if (player == null) return plugin.getLanguageManager().getMessage("placeholder_guest");
                net.crafter.plugin.core.model.PlayerData data = plugin.getPlayerManager().getPlayerData(player.getName());
                return (data != null && data.getRole() != null) ? data.getRole().getName() : plugin.getLanguageManager().getMessage("placeholder_guest");
            }
            case "user_id" -> {
                if (player == null) return plugin.getLanguageManager().getMessage("placeholder_na");
                net.crafter.plugin.core.model.PlayerData data = plugin.getPlayerManager().getPlayerData(player.getName());
                return (data != null && data.getId() != null) ? data.getId() : plugin.getLanguageManager().getMessage("placeholder_na");
            }
            case "chest_count" -> {
                if (player == null) return "0";
                List<net.crafter.plugin.core.model.ChestItem> chest = plugin.getMarketManager().getPlayerChest(player.getName());
                return chest != null ? String.valueOf(chest.size()) : "0";
            }
        }

        return null;
    }
}
