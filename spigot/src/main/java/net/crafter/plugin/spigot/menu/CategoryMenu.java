package net.crafter.plugin.spigot.menu;

import net.crafter.plugin.core.model.Category;
import net.crafter.plugin.spigot.CrafterSpigotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CategoryMenu extends MenuManager.BaseMenu {
    private final CrafterSpigotPlugin plugin;
    private final List<Category> categories;

    public CategoryMenu(CrafterSpigotPlugin plugin) {
        this.plugin = plugin;
        this.categories = plugin.getMarketManager().getCategories();
        this.inventory = Bukkit.createInventory(this, 27, plugin.getLanguageManager().getMessage("gui_shop_categories"));
        refresh();
    }

    public void refresh() {
        setupBackground();
        List<ItemStack> items = new ArrayList<>();
        for (Category category : categories) {
            items.add(createCategoryItem(category));
        }
        placeCentered(items);
    }

    private ItemStack createCategoryItem(Category category) {
        Material material;
        try {
            // Icon bilgisini Material'a çeviriyoruz (Örn: BOOK, DIAMOND)
            material = Material.valueOf(category.getIcon().toUpperCase());
        } catch (Exception e) {
            material = Material.BOOK;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui_category_name", category.getName()));
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(plugin.getLanguageManager().getMessage("gui_category_lore"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType().name().contains("GLASS_PANE")) return;

        Player player = (Player) event.getWhoClicked();
        
        // Tıklanan kategori isminden kategoriyi buluyoruz
        for (Category category : categories) {
            if (clicked.getItemMeta().getDisplayName().contains(category.getName())) {
                player.openInventory(new ShopMenu(plugin, category.getId()).getInventory());
                return;
            }
        }
    }
}
