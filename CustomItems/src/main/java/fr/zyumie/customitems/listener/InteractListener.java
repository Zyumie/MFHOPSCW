package fr.zyumie.customitems.listener;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Détecte les clics droits pour activer les mécaniques basées sur l'interaction :
 * - Épée Berserk : clic droit → activation du mode berserk
 */
public class InteractListener implements Listener {

    private final CustomItems plugin;

    public InteractListener(CustomItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Ne traite que la main principale
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        String heldId = ItemBuilder.getItemId(plugin, player.getInventory().getItemInMainHand());

        if (ItemRegistry.BERSERK_SWORD.equals(heldId)) {
            event.setCancelled(true);
            plugin.getBerserkHandler().activate(player);
        }
    }
}
