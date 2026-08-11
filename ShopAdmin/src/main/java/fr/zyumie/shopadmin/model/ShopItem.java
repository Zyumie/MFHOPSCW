package fr.zyumie.shopadmin.model;

import org.bukkit.Material;

import java.util.List;

public class ShopItem {

    private final String id;
    private final String categoryId;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final double buyPrice;
    private final double sellPrice;
    private int stock;
    private final int slot;
    private final int customModelData;
    private final String trimPattern;
    private final String trimMaterial;

    public ShopItem(String id, String categoryId, Material material, String name,
                    List<String> lore, double buyPrice, double sellPrice,
                    int stock, int slot, int customModelData,
                    String trimPattern, String trimMaterial) {
        this.id = id;
        this.categoryId = categoryId;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.stock = stock;
        this.slot = slot;
        this.customModelData = customModelData;
        this.trimPattern = trimPattern;
        this.trimMaterial = trimMaterial;
    }

    public String getId()           { return id; }
    public String getCategoryId()   { return categoryId; }
    public Material getMaterial()   { return material; }
    public String getName()         { return name; }
    public List<String> getLore()   { return lore; }
    public double getBuyPrice()     { return buyPrice; }
    public double getSellPrice()    { return sellPrice; }
    public int getStock()           { return stock; }
    public int getSlot()            { return slot; }
    public int getCustomModelData() { return customModelData; }
    public String getTrimPattern() { return trimPattern; }
    public String getTrimMaterial() { return trimMaterial; }

    public boolean canBuy()          { return buyPrice >= 0; }
    public boolean canSell()         { return sellPrice >= 0; }
    public boolean isUnlimitedStock(){ return stock == -1; }

    public boolean hasStock(int amount) {
        return isUnlimitedStock() || stock >= amount;
    }

    public void removeStock(int amount) {
        if (!isUnlimitedStock()) stock = Math.max(0, stock - amount);
    }

    public void addStock(int amount) {
        if (!isUnlimitedStock()) stock += amount;
    }
}