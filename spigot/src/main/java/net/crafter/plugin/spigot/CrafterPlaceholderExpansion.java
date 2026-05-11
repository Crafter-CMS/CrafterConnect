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
        return "1.0.1";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        StatisticsData stats = plugin.getStatsManager().getStats();
        if (stats == null) return "";

        switch (params.toLowerCase()) {
            case "total_users" -> {
                return String.valueOf(stats.getTotalUsers());
            }
            case "last_purchase_user" -> {
                List<StatisticsData.Purchase> purchases = stats.getLatest().getPurchases();
                return purchases.isEmpty() ? "-" : purchases.get(0).getUsername();
            }
            case "last_purchase_product" -> {
                List<StatisticsData.Purchase> purchases = stats.getLatest().getPurchases();
                return purchases.isEmpty() ? "-" : purchases.get(0).getProductName();
            }
            case "top_loader_user" -> {
                List<StatisticsData.TopLoader> top = stats.getTopCreditLoaders();
                return top.isEmpty() ? "-" : top.get(0).getUsername();
            }
            case "top_loader_amount" -> {
                List<StatisticsData.TopLoader> top = stats.getTopCreditLoaders();
                return top.isEmpty() ? "0" : String.valueOf(top.get(0).getTotalAmount());
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
        }

        return null;
    }
}
