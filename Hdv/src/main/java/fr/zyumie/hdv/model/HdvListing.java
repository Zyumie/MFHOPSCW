package fr.zyumie.hdv.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class HdvListing {

    private final String id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private double price;
    private final long createdAt;
    private final long expiresAt;
    private boolean sold;
    private boolean cancelled;

    public HdvListing(String id, UUID sellerUuid, String sellerName,
                      ItemStack item, double price, long createdAt, long expiresAt) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.sold = false;
        this.cancelled = false;
    }

    public String getId()            { return id; }
    public UUID getSellerUuid()      { return sellerUuid; }
    public String getSellerName()    { return sellerName; }
    public ItemStack getItem()       { return item.clone(); }
    public double getPrice()         { return price; }
    public void setPrice(double p)   { this.price = p; }
    public long getCreatedAt()       { return createdAt; }
    public long getExpiresAt()       { return expiresAt; }
    public boolean isSold()          { return sold; }
    public void setSold(boolean b)   { this.sold = b; }
    public boolean isCancelled()     { return cancelled; }
    public void setCancelled(boolean b) { this.cancelled = b; }

    public boolean isExpired()       { return System.currentTimeMillis() > expiresAt; }
    public boolean isActive()        { return !sold && !cancelled && !isExpired(); }

    /** Temps restant formaté (ex: "2j 3h", "45min") */
    public String getTimeRemaining() {
        long remaining = (expiresAt - System.currentTimeMillis()) / 1000;
        if (remaining <= 0) return "Expiré";
        long days    = remaining / 86400;
        long hours   = (remaining % 86400) / 3600;
        long minutes = (remaining % 3600) / 60;
        if (days > 0)  return days + "j " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "min";
        return minutes + "min";
    }
}
