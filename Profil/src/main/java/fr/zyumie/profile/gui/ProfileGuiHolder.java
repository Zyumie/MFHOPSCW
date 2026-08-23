package fr.zyumie.profile.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Identifie les inventaires ouverts par /profile en mode GUI, pour que le listener de clic
 * les reconnaisse sans comparer le titre affiché (fragile si le titre change dans gui.yml).
 * Même convention que les autres GUI de l'écosystème (ShopAdmin, Hdv, ...).
 */
public class ProfileGuiHolder implements InventoryHolder {

    private final UUID targetUuid;
    private Inventory inventory;

    public ProfileGuiHolder(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    /** Doit être appelé juste après Bukkit.createInventory(this, ...). */
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }
}
