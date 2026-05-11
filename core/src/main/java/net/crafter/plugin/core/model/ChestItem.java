package net.crafter.plugin.core.model;

public class ChestItem {
    private String id;
    private Product product;
    private boolean used;

    public String getId() { return id; }
    public Product getProduct() { return product; }
    public boolean isUsed() { return used; }

    // Helper for easier access
    public String getName() {
        return product != null ? product.getName() : "Unknown Item";
    }

    public String getStatus() {
        return used ? "Kullanıldı" : "Bekliyor";
    }
}
