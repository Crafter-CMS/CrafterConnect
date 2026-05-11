package net.crafter.plugin.core;

import net.crafter.plugin.core.model.Category;
import net.crafter.plugin.core.model.ChestItem;
import net.crafter.plugin.core.model.Product;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarketManager {
    private List<Product> products = new ArrayList<>();
    private List<Category> categories = new ArrayList<>();
    private final Map<String, List<ChestItem>> playerChests = new ConcurrentHashMap<>();
    private boolean isLoaded = false;
    private Runnable onUpdate;

    public void setOnUpdate(Runnable onUpdate) { this.onUpdate = onUpdate; }

    public void updateMarket(List<Product> products, List<Category> categories) {
        this.products = products;
        this.categories = categories;
        this.isLoaded = true;
        if (onUpdate != null) onUpdate.run();
    }

    public boolean isLoaded() { return isLoaded; }

    public void updatePlayerChest(String username, List<ChestItem> items) {
        playerChests.put(username.toLowerCase(), items);
    }

    public List<Product> getProducts() { return products; }
    public List<Category> getCategories() { return categories; }
    public List<ChestItem> getPlayerChest(String username) {
        return playerChests.getOrDefault(username.toLowerCase(), Collections.emptyList());
    }
}
