package fr.zyumie.customitems.handler.armor;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Armure Voleuse d'Âmes :
 * - Kill de joueur → +2 cœurs (via AttributeModifier sur MAX_HEALTH, cumulable)
 * - Pièce retirée ou mort → reset immédiat de tout le bonus
 */
public class SoulArmorHandler {

    private static final String MOD_NAME = "soul_armor_bonus";

    // 💡 Changement ici : CustomItems au lieu de JavaPlugin
    private final CustomItems plugin;

    // UUID → nombre de kills accumulés
    private final Map<UUID, Integer> kills = new HashMap<>();

    // 💡 Changement ici : CustomItems dans le constructeur
    public SoulArmorHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    /** Appelé à chaque kill d'un autre joueur */
    public void onKill(Player player, Entity victim) {
        if (!plugin.getCustomArmorConfig().getBoolean("armors.soul.enabled", true)) return;
        if (!wearingFullSet(player)) return;

        // 🛡️ SÉCURITÉ SMP : Ignore les mobs pour empêcher l'abus en ferme à mobs
        if (!(victim instanceof Player)) return;

        double hpPerKill = plugin.getCustomArmorConfig().getDouble("armors.soul.hp-per-kill", 4.0);
        int maxHearts    = plugin.getCustomArmorConfig().getInt("armors.soul.max-bonus-hearts", 10);
        double maxBonusHP = maxHearts > 0 ? maxHearts * 2.0 : 0; // 1 cœur = 2 PV

        int count = kills.merge(player.getUniqueId(), 1, Integer::sum);
        double totalBonus = count * hpPerKill;

        if (maxBonusHP > 0 && totalBonus > maxBonusHP) {
            totalBonus = maxBonusHP;
            kills.put(player.getUniqueId(), (int)(maxBonusHP / hpPerKill));
        }

        applyBonus(player, totalBonus);
        send(player, plugin.getCustomArmorConfig().getString("armors.soul.messages.kill-bonus", "")
                .replace("{total}", String.valueOf(count)));
    }

    /** Appelé quand une pièce d'armure est retirée */
    public void onUnequip(Player player) {
        if (kills.containsKey(player.getUniqueId())) {
            resetBonus(player);
            send(player, plugin.getCustomArmorConfig().getString("armors.soul.messages.reset-unequip", ""));
        }
    }

    /** Appelé à la mort du joueur */
    public void onDeath(Player player) {
        if (kills.containsKey(player.getUniqueId())) {
            resetBonus(player);
            send(player, plugin.getCustomArmorConfig().getString("armors.soul.messages.reset-death", ""));
        }
    }

    // ── Internes ──────────────────────────────────────────────────────

    private void applyBonus(Player player, double bonus) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
        attr.getModifiers().stream()
                .filter(m -> m.key().equals(key))
                .toList()
                .forEach(attr::removeModifier);

        attr.addModifier(new AttributeModifier(key, bonus, AttributeModifier.Operation.ADD_NUMBER));
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
        if (player.getHealth() > attr.getValue()) player.setHealth(attr.getValue());
    }

    private boolean wearingFullSet(Player player) {
        int c = 0;
        for (ItemStack p : player.getInventory().getArmorContents())
            if (ItemBuilder.isCustomItem(plugin, p, ItemRegistry.SOUL_ARMOR)) c++;
        return c == 4;
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }

    public void cleanup(Player player) {
        resetBonus(player);
    }
}