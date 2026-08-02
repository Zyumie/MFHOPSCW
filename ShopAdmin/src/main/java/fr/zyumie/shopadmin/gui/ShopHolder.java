package fr.zyumie.shopadmin.gui;

import fr.zyumie.shopadmin.model.ShopItem;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopHolder implements InventoryHolder {

    public enum Type { MAIN, QUANTITY }

    private final Type type;
    private String categoryId;
    private int page;
    private ShopItem pendingItem;
    private boolean buying;

    public ShopHolder(Type type, String categoryId, int page) {
        this.type = type;
        this.categoryId = categoryId;
        this.page = page;
    }

    @Override public Inventory getInventory() { return null; }

    public Type getType()                     { return type; }
    public String getCategoryId()             { return categoryId; }
    public void setCategoryId(String c)       { this.categoryId = c; }
    public int getPage()                      { return page; }
    public void setPage(int p)                { this.page = p; }
    public ShopItem getPendingItem()          { return pendingItem; }
    public void setPendingItem(ShopItem i)    { this.pendingItem = i; }
    public boolean isBuying()                 { return buying; }
    public void setBuying(boolean b)          { this.buying = b; }
}
