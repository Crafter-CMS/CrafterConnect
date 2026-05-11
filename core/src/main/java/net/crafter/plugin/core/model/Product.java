package net.crafter.plugin.core.model;

public class Product {
    private String id;
    private String name;
    private String description;
    private double price;
    @com.google.gson.annotations.SerializedName(value = "category_id", alternate = {"categoryId", "category"})
    private String category_id;
    private String image;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public String getCategoryId() { return category_id; }
    public String getImage() { return image; }
}
