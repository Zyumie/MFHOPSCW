package fr.zyumie.customitems.handler.weapon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Arc Explosif : flèche qui touche un joueur → explosion de zone (AoE), sans dégât de blocs.
 */
public class ExplosiveBowHandler {

    private final JavaPlugin plugin;

    public ExplosiveBowHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void triggerExplosion(Location location, Player shooter) {
        if (!plugin.getConfig().getBoolean("weapons.explosive-bow.enabled", true)) return;

        double radius  = plugin.getConfig().getDouble("weapons.explosive-bow.explosion-radius", 4.0);
        double damage  = plugin.getConfig().getDouble("weapons.explosive-bow.explosion-damage", 6.0);
        boolean realEx = plugin.getConfig().getBoolean("weapons.explosive-bow.real-explosion", false);

        // Vrais dégâts de blocs si configuré
        if (realEx) {
            location.getWorld().createExplosion(location, (float) radius, false, false);
        } else {
            // Particules + son uniquement (pas de destruction de blocs)
            String particleName = plugin.getConfig().getString(
                    "weapons.explosive-bow.explosion-particle", "EXPLOSION_LARGE");
            try {
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                location.getWorld().spawnParticle(particle, location, 10, 1, 1, 1);
            } catch (IllegalArgumentException ignored) {
                location.getWorld().spawnParticle(Particle.EXPLOSION, location, 5, 1, 1, 1);
            }
            location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        }

        // Dégâts AoE aux entités proches (sauf le tireur)
        List<Entity> affected = new ArrayList<>(
                location.getWorld().getNearbyEntities(location, radius, radius, radius));
        int hits = 0;
        for (Entity e : affected) {
            if (!(e instanceof LivingEntity living)) continue;
            if (e.equals(shooter)) continue;
            living.setNoDamageTicks(0);
            living.damage(damage, shooter);
            hits++;
        }

        if (hits > 0) {
            send(shooter, plugin.getConfig().getString("weapons.explosive-bow.messages.explosion", "")
                    .replace("{hits}", String.valueOf(hits)));
        }
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }
}
