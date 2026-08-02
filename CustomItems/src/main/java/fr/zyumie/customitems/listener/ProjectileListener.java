package fr.zyumie.customitems.listener;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Arc Explosif & Arc Ender :
 * - À l'envoi : tag PDC sur la flèche avec le type d'arc
 * - À l'impact : déclenche l'effet correspondant si la cible est un joueur
 */
public class ProjectileListener implements Listener {

    private static final String ARROW_TAG = "custom_bow_type";

    private final CustomItems plugin;

    public ProjectileListener(CustomItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        String bowId = ItemBuilder.getItemId(plugin, shooter.getInventory().getItemInMainHand());
        if (bowId == null) return;

        if (ItemRegistry.EXPLOSIVE_BOW.equals(bowId) || ItemRegistry.ENDER_BOW.equals(bowId)) {
            // Tague la flèche avec le type d'arc
            arrow.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, ARROW_TAG),
                    PersistentDataType.STRING, bowId);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        // Lit le tag de l'arc
        String bowType = arrow.getPersistentDataContainer().get(
                new NamespacedKey(plugin, ARROW_TAG), PersistentDataType.STRING);
        if (bowType == null) return;

        // Cible = joueur
        if (!(event.getHitEntity() instanceof Player target)) return;
        // Pas de self-hit
        if (target.equals(shooter)) return;

        switch (bowType) {
            case ItemRegistry.EXPLOSIVE_BOW ->
                plugin.getExplosiveBowHandler().triggerExplosion(arrow.getLocation(), shooter);
            case ItemRegistry.ENDER_BOW ->
                plugin.getEnderBowHandler().teleportBehindTarget(shooter, target);
        }

        // On retire la flèche pour éviter les doubles déclenchements
        arrow.remove();
        event.setCancelled(true);
    }
}
