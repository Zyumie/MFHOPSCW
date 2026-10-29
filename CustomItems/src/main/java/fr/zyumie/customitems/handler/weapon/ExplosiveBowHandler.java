package fr.zyumie.customitems.handler.weapon;

import fr.zyumie.customitems.CustomItems; // <-- Import CustomItems
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ExplosiveBowHandler {

    private final CustomItems plugin; // <-- CustomItems au lieu de JavaPlugin

    public ExplosiveBowHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    public void triggerExplosion(Location location, Player shooter) {
        if (!plugin.getWeaponConfig().getBoolean("weapons.explosive-bow.enabled", true)) return;

        double radius  = plugin.getWeaponConfig().getDouble("weapons.explosive-bow.explosion-radius", 4.0);
        double damage  = plugin.getWeaponConfig().getDouble("weapons.explosive-bow.explosion-damage", 6.0);
        boolean realEx = plugin.getWeaponConfig().getBoolean("weapons.explosive-bow.real-explosion", false);

        if (realEx) {
            location.getWorld().createExplosion(location, (float) radius, false, false);
        } else {
            String particleName = plugin.getWeaponConfig().getString(
                    "weapons.explosive-bow.explosion-particle", "EXPLOSION_LARGE");
            try {
                Particle particle = Particle.valueOf(particleName.toUpperCase());
                location.getWorld().spawnParticle(particle, location, 10, 1, 1, 1);
            } catch (IllegalArgumentException ignored) {
                location.getWorld().spawnParticle(Particle.EXPLOSION, location, 5, 1, 1, 1);
            }
            location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
        }

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
            send(shooter, plugin.getWeaponConfig().getString("weapons.explosive-bow.messages.explosion", "")
                    .replace("{hits}", String.valueOf(hits)));
        }
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }
}