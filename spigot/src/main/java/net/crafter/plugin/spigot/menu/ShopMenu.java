package net.crafter.plugin.spigot.menu;

import net.crafter.plugin.core.model.Product;
import net.crafter.plugin.spigot.CrafterSpigotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShopMenu extends MenuManager.BaseMenu {
    private final CrafterSpigotPlugin plugin;
    private final String categoryId;
    private List<Product> filteredProducts;

    public ShopMenu(CrafterSpigotPlugin plugin, String categoryId) {
        this.plugin = plugin;
        this.categoryId = categoryId;
        this.inventory = Bukkit.createInventory(this, 54, plugin.getLanguageManager().getMessage("gui_shop_products"));
        refresh();
    }

    public void refresh() {
        setupBackground();
        
        this.filteredProducts = plugin.getMarketManager().getProducts().stream()
                .filter(p -> p.getCategoryId() != null && p.getCategoryId().equals(categoryId))
                .collect(Collectors.toList());

        List<ItemStack> items = new ArrayList<>();
        for (Product product : filteredProducts) {
            items.add(createProductItem(product));
        }
        
        placeCentered(items);

        // Geri dön butonu
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui_back_button"));
            back.setItemMeta(meta);
        }
        inventory.setItem(49, back);
    }

    private ItemStack createProductItem(Product product) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getLanguageManager().getMessage("gui_product_name", product.getName()));
            List<String> lore = new ArrayList<>();
            if (product.getDescription() != null && !product.getDescription().isEmpty()) {
                lore.add(plugin.getLanguageManager().getMessage("gui_product_description", product.getDescription()));
                lore.add("");
            }
            lore.add(plugin.getLanguageManager().getMessage("gui_product_price", String.format("%.2f", product.getPrice())));
            lore.add("");
            lore.add(plugin.getLanguageManager().getMessage("gui_product_buy"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleMenu(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();

        if (clicked.getType() == Material.ARROW) {
            player.openInventory(new CategoryMenu(plugin).getInventory());
            return;
        }

        if (clicked.getType().name().contains("GLASS_PANE")) return;

        // Ürünü bul ve satın alma isteği gönder
        for (Product product : filteredProducts) {
            if (clicked.getItemMeta().getDisplayName().contains(product.getName())) {
                player.sendMessage(plugin.getLanguageManager().getMessage("prefix") + 
                        plugin.getLanguageManager().getMessage("purchase_processing", product.getName()));
                plugin.getWsClient().purchaseProduct(player.getName(), product.getId());
                player.closeInventory();
                return;
            }
        }
    }
}
