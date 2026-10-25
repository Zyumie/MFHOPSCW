package fr.zyumie.customitems.listener;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Gère les kills pour :
 * - Épée Céleste : propulsion après kill
 * - Armure d'Âmes : +2 cœurs par kill
 * - Mort du joueur : reset Âmes + nettoyage Berserk/Céleste
 */
public class KillListener implements Listener {

    private final CustomItems plugin;

    public KillListener(CustomItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        String heldId = ItemBuilder.getItemId(plugin, killer.getInventory().getItemInMainHand());

        // Épée Céleste → propulsion
        if (ItemRegistry.CELESTIAL_SWORD.equals(heldId)) {
            plugin.getCelestialHandler().onKillWithSword(killer);
        }

        // Armure d'Âmes → +HP
        plugin.getSoulHandler().onKill(killer, event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();

        // Reset Armure d'Âmes
        plugin.getSoulHandler().onDeath(dead);

        // Nettoyage Ghost
        plugin.getGhostHandler().cleanup(dead);

        // Nettoyage Berserk
        plugin.getBerserkHandler().cleanup(dead);

        // Nettoyage Céleste
        plugin.getCelestialHandler().removeFromFlight(dead);
    }
}
