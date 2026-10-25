package fr.zyumie.faction.listener;

import fr.zyumie.faction.model.Faction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class FactionPvpListener implements Listener {

    private final FactionManager manager;

    public FactionPvpListener(FactionManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        Player damager = null;
        if (event.getDamager() instanceof Player p) {
            damager = p;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            damager = p;
        }

        if (damager == null) return;

        Faction targetFaction = manager.getFactionOf(target.getUniqueId());
        Faction damagerFaction = manager.getFactionOf(damager.getUniqueId());

        // Si les deux joueurs sont dans la même faction
        if (targetFaction != null && targetFaction.equals(damagerFaction)) {
            // Si le PvP de faction n'est pas activé, on annule les dégâts
            if (!targetFaction.isPvpEnabled()) {
                event.setCancelled(true);
            }
        }
    }
}