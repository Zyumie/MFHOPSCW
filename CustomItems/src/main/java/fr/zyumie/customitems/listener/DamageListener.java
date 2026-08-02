package fr.zyumie.customitems.listener;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Gère tous les événements de dégâts liés aux items custom :
 * - Ghost Armor : révèle quand touché
 * - Rescue Armor : téléportation d'urgence quand HP ≤ seuil
 * - Celestial Sword : slam en chute (mace → one-shot)
 * - Celestial : annule les dégâts de chute si en vol céleste
 */
public class DamageListener implements Listener {

    private final CustomItems plugin;

    public DamageListener(CustomItems plugin) {
        this.plugin = plugin;
    }

    // ── Dégâts entité → entité ────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {

        // ── Attaquant = joueur ──
        if (event.getDamager() instanceof Player attacker) {
            String heldId = ItemBuilder.getItemId(plugin,
                    attacker.getInventory().getItemInMainHand());

            // Masse Céleste en vol → slam
            if (ItemRegistry.CELESTIAL_MACE.equals(heldId)
                    && event.getEntity() instanceof LivingEntity target) {
                plugin.getCelestialHandler().onHitWithMace(event, attacker,
                        (LivingEntity) event.getEntity());
                return;
            }
        }

        // ── Victime = joueur ──
        if (event.getEntity() instanceof Player victim && event.getDamage() > 0) {

            // Ghost Armor : révèle si touché
            plugin.getGhostHandler().onHit(victim);

            // Rescue Armor : vérifie HP après application des dégâts (délai 1 tick)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) plugin.getRescueHandler().checkRescue(victim);
            }, 1L);
        }
    }

    // ── Dégâts génériques (chute, feu, etc.) ──────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Annule les dégâts de chute pour le joueur en vol céleste
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && plugin.getCelestialHandler().isInFlight(player)) {
            event.setCancelled(true);
            plugin.getCelestialHandler().onFallLanding(player);
            return;
        }

        // Rescue : vérifie HP après dégâts non-entité (chute, feu, etc.)
        if (event.getDamage() > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) plugin.getRescueHandler().checkRescue(player);
            }, 1L);
        }
    }
}
