package fr.zyumie.customitems.handler.armor;

import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import fr.zyumie.customitems.util.SafeTeleport;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Armure de Sauvetage — téléportation d'urgence quand HP ≤ seuil (défaut: 1 cœur).
 */
public class RescueArmorHandler {

    private final JavaPlugin plugin;
    // UUID → timestamp de dernière téléportation (ms)
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RescueArmorHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Appelé depuis EntityDamageEvent APRÈS application des dégâts.
     * Vérifie si le joueur doit être téléporté.
     */
    public void checkRescue(Player player) {
        if (!plugin.getConfig().getBoolean("armors.rescue.enabled", true)) return;
        if (!wearingAnyPiece(player)) return;

        double threshold = plugin.getConfig().getDouble("armors.rescue.trigger-health", 2.0);
        if (player.getHealth() > threshold) return;

        long cooldownMs = plugin.getConfig().getLong("armors.rescue.cooldown-seconds", 30) * 1000L;
        long now        = System.currentTimeMillis();
        long last       = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < cooldownMs) {
            long remaining = (cooldownMs - (now - last)) / 1000;
            send(player, plugin.getConfig().getString("armors.rescue.messages.cooldown", "")
                    .replace("{remaining}", String.valueOf(remaining)));
            return;
        }

        // Cherche une position sûre
        double radius = plugin.getConfig().getDouble("armors.rescue.teleport-radius", 15.0);
        Location safe = SafeTeleport.findSafe(player.getLocation(), radius, 30);

        if (safe == null) {
            send(player, "&cAucune position sûre trouvée à proximité !");
            return;
        }

        cooldowns.put(player.getUniqueId(), now);

        // Effets + téléportation
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 30, 0.5, 1, 0.5);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.teleport(safe);
        player.getWorld().spawnParticle(Particle.PORTAL, safe, 30, 0.5, 1, 0.5);

        send(player, plugin.getConfig().getString("armors.rescue.messages.triggered", ""));
    }

    private boolean wearingAnyPiece(Player player) {
        for (ItemStack p : player.getInventory().getArmorContents())
            if (ItemBuilder.isCustomItem(plugin, p, ItemRegistry.RESCUE_ARMOR)) return true;
        return false;
    }

    private void send(Player p, String legacy) {
        if (legacy == null || legacy.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(
                legacy.replace("&8","<dark_gray>").replace("&a","<green>")
                      .replace("&c","<red>").replace("&e","<yellow>").replace("&7","<gray>")));
    }
}
