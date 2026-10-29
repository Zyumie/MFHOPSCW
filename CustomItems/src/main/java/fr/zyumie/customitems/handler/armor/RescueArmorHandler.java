package fr.zyumie.customitems.handler.armor;

import fr.zyumie.customitems.CustomItems; // <-- Import CustomItems
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import fr.zyumie.customitems.util.SafeTeleport;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RescueArmorHandler {

    private final CustomItems plugin; // <-- CustomItems au lieu de JavaPlugin
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RescueArmorHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    public void checkRescue(Player player) {
        if (!plugin.getCustomArmorConfig().getBoolean("armors.rescue.enabled", true)) return;
        if (!wearingAnyPiece(player)) return;

        double threshold = plugin.getCustomArmorConfig().getDouble("armors.rescue.trigger-health", 2.0);
        if (player.getHealth() > threshold) return;

        long cooldownMs = plugin.getCustomArmorConfig().getLong("armors.rescue.cooldown-seconds", 30) * 1000L;
        long now        = System.currentTimeMillis();
        long last       = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < cooldownMs) {
            long remaining = (cooldownMs - (now - last)) / 1000;
            send(player, plugin.getCustomArmorConfig().getString("armors.rescue.messages.cooldown", "")
                    .replace("{remaining}", String.valueOf(remaining)));
            return;
        }

        double radius = plugin.getCustomArmorConfig().getDouble("armors.rescue.teleport-radius", 15.0);
        Location safe = SafeTeleport.findSafe(player.getLocation(), radius, 30);

        if (safe == null) {
            send(player, "<red>Aucune position sûre trouvée à proximité !");
            return;
        }

        cooldowns.put(player.getUniqueId(), now);

        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 30, 0.5, 1, 0.5);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.teleport(safe);
        player.getWorld().spawnParticle(Particle.PORTAL, safe, 30, 0.5, 1, 0.5);

        send(player, plugin.getCustomArmorConfig().getString("armors.rescue.messages.triggered", ""));
    }

    private boolean wearingAnyPiece(Player player) {
        for (ItemStack p : player.getInventory().getArmorContents())
            if (ItemBuilder.isCustomItem(plugin, p, ItemRegistry.RESCUE_ARMOR)) return true;
        return false;
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }
}