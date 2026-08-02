package fr.zyumie.hdv.gui;

import fr.zyumie.hdv.model.HdvListing;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class HdvHolder implements InventoryHolder {

    public enum Type { MAIN, CONFIRM_BUY, CREATE }

    private final Type type;
    private boolean myListings;
    private int page;
    private HdvListing pendingListing;
    private ItemStack pendingItem;
    private double pendingPrice;
    private int pendingDuration;

    public HdvHolder(Type type) { this.type = type; }

    @Override public Inventory getInventory() { return null; }

    public Type getType()                       { return type; }
    public boolean isMyListings()               { return myListings; }
    public void setMyListings(boolean b)        { this.myListings = b; }
    public int getPage()                        { return page; }
    public void setPage(int p)                  { this.page = p; }
    public HdvListing getPendingListing()       { return pendingListing; }
    public void setPendingListing(HdvListing l) { this.pendingListing = l; }
    public ItemStack getPendingItem()           { return pendingItem; }
    public void setPendingItem(ItemStack i)     { this.pendingItem = i; }
    public double getPendingPrice()             { return pendingPrice; }
    public void setPendingPrice(double p)       { this.pendingPrice = p; }
    public int getPendingDuration()             { return pendingDuration; }
    public void setPendingDuration(int d)       { this.pendingDuration = d; }
}
