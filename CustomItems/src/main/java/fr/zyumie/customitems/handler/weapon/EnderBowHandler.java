package fr.zyumie.customitems.handler.weapon;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Arc Ender : flèche qui touche un joueur → le tireur est téléporté juste derrière la cible.
 */
public class EnderBowHandler {

    private final JavaPlugin plugin;

    public EnderBowHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void teleportBehindTarget(Player shooter, Player target) {
        if (!plugin.getConfig().getBoolean("weapons.ender-bow.enabled", true)) return;

        double offset = plugin.getConfig().getDouble("weapons.ender-bow.teleport-offset", 2.0);

        // Direction où regarde la cible
        Vector facingDir = target.getLocation().getDirection().normalize();
        // Se place derrière la cible (direction opposée)
        Location behind = target.getLocation().clone()
                .subtract(facingDir.multiply(offset));
        behind.setY(target.getLocation().getY());
        // Le tireur fait face à la cible après la téléportation
        behind.setDirection(facingDir);

        // Effets départ
        String particleName = plugin.getConfig().getString(
                "weapons.ender-bow.teleport-particle", "PORTAL");
        Particle particle;
        try { particle = Particle.valueOf(particleName.toUpperCase()); }
        catch (IllegalArgumentException e) { particle = Particle.PORTAL; }

        shooter.getWorld().spawnParticle(particle, shooter.getLocation(), 20, 0.5, 1, 0.5, 0.1);
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        // Téléportation
        shooter.teleport(behind);

        // Effets arrivée
        shooter.getWorld().spawnParticle(particle, behind, 20, 0.5, 1, 0.5, 0.1);

        send(shooter, plugin.getConfig().getString("weapons.ender-bow.messages.teleported", "")
                .replace("{target}", target.getName()));
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }
}
