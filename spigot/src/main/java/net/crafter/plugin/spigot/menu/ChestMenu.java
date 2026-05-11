package net.crafter.plugin.spigot.menu;

import net.crafter.plugin.core.model.ChestItem;
import net.crafter.plugin.spigot.CrafterSpigotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ChestMenu extends MenuManager.BaseMenu {
    private final CrafterSpigotPlugin plugin;
    private final Player player;

    public ChestMenu(CrafterSpigotPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54, plugin.getLanguageManager().getMessage("gui_chest_title"));
        refresh();
    }

    public void refresh() {
        setupBackground();
        List<ChestItem> items = plugin.getMarketManager().getPlayerChest(player.getName());
        
        List<ItemStack> stacks = new ArrayList<>();
        for (ChestItem item : items) {
            if (!item.isUsed()) {
                stacks.add(createChestItem(item));
            }
        }
        placeCentered(stacks);
    }

    private ItemStack createChestItem(ChestItem item) {
        ItemStack stack = new ItemStack(Material.CHEST);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui_chest_item_name", item.getName()));
            List<String> lore = new ArrayList<>();
            String status = item.isUsed() ? 
                    plugin.getLanguageManager().getMessage("gui_chest_status_used") : 
                    plugin.getLanguageManager().getMessage("gui_chest_status_pending");
            
            lore.add(plugin.getLanguageManager().getMessage("gui_chest_status", status));
            lore.add("");
            lore.add(plugin.getLanguageManager().getMessage("gui_chest_claim"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType().name().contains("GLASS_PANE")) return;

        List<ChestItem> items = plugin.getMarketManager().getPlayerChest(player.getName());
        
        for (ChestItem item : items) {
            if (clicked.getItemMeta().getDisplayName().contains(item.getName())) {
                player.sendMessage(plugin.getLanguageManager().getMessage("prefix") + 
                        plugin.getLanguageManager().getMessage("item_using", item.getName()));
                plugin.getWsClient().useChestItem(player.getName(), item.getId());
                player.closeInventory();
                return;
            }
        }
    }
}
