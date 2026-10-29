package fr.zyumie.customitems.handler.weapon;

import fr.zyumie.customitems.CustomItems; // <-- Import CustomItems
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class EnderBowHandler {

    private final CustomItems plugin; // <-- CustomItems au lieu de JavaPlugin

    public EnderBowHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    public void teleportBehindTarget(Player shooter, Player target) {
        if (!plugin.getWeaponConfig().getBoolean("weapons.ender-bow.enabled", true)) return;

        double offset = plugin.getWeaponConfig().getDouble("weapons.ender-bow.teleport-offset", 2.0);

        Vector facingDir = target.getLocation().getDirection().normalize();
        Location behind = target.getLocation().clone()
                .subtract(facingDir.multiply(offset));
        behind.setY(target.getLocation().getY());
        behind.setDirection(facingDir);

        String particleName = plugin.getWeaponConfig().getString(
                "weapons.ender-bow.teleport-particle", "PORTAL");
        Particle particle;
        try { particle = Particle.valueOf(particleName.toUpperCase()); }
        catch (IllegalArgumentException e) { particle = Particle.PORTAL; }

        shooter.getWorld().spawnParticle(particle, shooter.getLocation(), 20, 0.5, 1, 0.5, 0.1);
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        shooter.teleport(behind);

        shooter.getWorld().spawnParticle(particle, behind, 20, 0.5, 1, 0.5, 0.1);

        send(shooter, plugin.getWeaponConfig().getString("weapons.ender-bow.messages.teleported", "")
                .replace("{target}", target.getName()));
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }
}