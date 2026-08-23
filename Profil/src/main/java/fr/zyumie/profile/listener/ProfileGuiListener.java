package fr.zyumie.profile.listener;

import fr.zyumie.profile.gui.ProfileGuiHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Le GUI de /profile est un affichage en lecture seule : on annule tout clic ou glisser-déposer
 * à l'intérieur, pour empêcher de retirer/déplacer les items d'information.
 * Identification par InventoryHolder (pas par titre), comme le reste de l'écosystème.
 */
public class ProfileGuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof ProfileGuiHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ProfileGuiHolder) {
            e.setCancelled(true);
        }
    }
}
