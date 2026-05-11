package net.crafter.plugin.spigot.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MenuManager implements Listener {

    public void openMenu(Player player, Inventory inventory) {
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getInventory().getHolder() instanceof BaseMenu) {
            event.setCancelled(true);
            BaseMenu menu = (BaseMenu) event.getInventory().getHolder();
            menu.handleMenu(event);
        }
    }

    public abstract static class BaseMenu implements InventoryHolder {
        protected Inventory inventory;

        public abstract void handleMenu(InventoryClickEvent event);

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        protected void setupBackground() {
            ItemStack filler = new ItemStack(Material.valueOf("BLACK_STAINED_GLASS_PANE"));
            org.bukkit.inventory.meta.ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(" ");
                filler.setItemMeta(meta);
            }
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        }

        protected void placeCentered(List<ItemStack> items) {
            if (items.isEmpty()) return;

            int size = items.size();
            int rows = (int) Math.ceil(size / 7.0);
            int startRow = (inventory.getSize() / 9 - rows) / 2;
            
            for (int i = 0; i < size; i++) {
                int row = startRow + (i / 7);
                int col = 1 + (i % 7); // 1-7 arası (kenarları boş bırakmak için)
                int slot = row * 9 + col;
                if (slot < inventory.getSize()) {
                    inventory.setItem(slot, items.get(i));
                }
            }
        }
    }
}
