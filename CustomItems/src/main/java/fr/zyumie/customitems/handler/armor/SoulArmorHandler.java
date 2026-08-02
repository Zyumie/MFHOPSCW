package fr.zyumie.customitems.handler.armor;

import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Armure Voleuse d'Âmes :
 * - Kill → +2 cœurs (via AttributeModifier sur MAX_HEALTH, cumulable)
 * - Pièce retirée ou mort → reset immédiat de tout le bonus
 */
public class SoulArmorHandler {

    private static final String MOD_NAME = "soul_armor_bonus";

    private final JavaPlugin plugin;
    // UUID → nombre de kills accumulés
    private final Map<UUID, Integer> kills = new HashMap<>();

    public SoulArmorHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Appelé à chaque kill d'entité (mob ou joueur) */
    public void onKill(Player player) {
        if (!plugin.getConfig().getBoolean("armors.soul.enabled", true)) return;
        if (!wearingFullSet(player)) return;

        double hpPerKill = plugin.getConfig().getDouble("armors.soul.hp-per-kill", 4.0);
        double maxBonus  = plugin.getConfig().getDouble("armors.soul.max-bonus-hp", 0);

        int count = kills.merge(player.getUniqueId(), 1, Integer::sum);
        double totalBonus = count * hpPerKill;

        if (maxBonus > 0 && totalBonus > maxBonus) {
            totalBonus = maxBonus;
            kills.put(player.getUniqueId(), (int)(maxBonus / hpPerKill));
        }

        applyBonus(player, totalBonus);
        send(player, plugin.getConfig().getString("armors.soul.messages.kill-bonus", "")
                .replace("{total}", String.valueOf(count)));
    }

    /** Appelé quand une pièce d'armure est retirée */
    public void onUnequip(Player player) {
        if (kills.containsKey(player.getUniqueId())) {
            resetBonus(player);
            send(player, plugin.getConfig().getString("armors.soul.messages.reset-unequip", ""));
        }
    }

    /** Appelé à la mort du joueur */
    public void onDeath(Player player) {
        if (kills.containsKey(player.getUniqueId())) {
            resetBonus(player);
            send(player, plugin.getConfig().getString("armors.soul.messages.reset-death", ""));
        }
    }

    // ── Internes ──────────────────────────────────────────────────────

    private void applyBonus(Player player, double bonus) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        // Retire l'ancien modificateur s'il existe
        NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
        attr.getModifiers().stream()
                .filter(m -> m.key().equals(key))
                .toList()
                .forEach(attr::removeModifier);

        // Applique le nouveau modificateur
        attr.addModifier(new AttributeModifier(key, bonus, AttributeModifier.Operation.ADD_NUMBER));
        // S'assure que le HP actuel ne dépasse pas le nouveau max
        if (player.getHealth() > attr.getValue()) player.setHealth(attr.getValue());
    }

    private void resetBonus(Player player) {
        kills.remove(player.getUniqueId());
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
        attr.getModifiers().stream()
                .filter(m -> m.key().equals(key))
                .toList()
                .forEach(attr::removeModifier);
        // Reclampe la vie si elle dépasse le nouveau max
        if (player.getHealth() > attr.getValue()) player.setHealth(attr.getValue());
    }

    private boolean wearingFullSet(Player player) {
        int c = 0;
        for (ItemStack p : player.getInventory().getArmorContents())
            if (ItemBuilder.isCustomItem(plugin, p, ItemRegistry.SOUL_ARMOR)) c++;
        return c == 4;
    }

    private void send(Player p, String legacy) {
        if (legacy == null || legacy.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(
                legacy.replace("&5","<dark_purple>").replace("&c","<red>")
                      .replace("&e","<yellow>").replace("&8","<dark_gray>").replace("&7","<gray>")));
    }
}
