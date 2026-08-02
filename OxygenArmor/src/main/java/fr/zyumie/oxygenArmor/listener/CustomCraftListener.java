package fr.zyumie.oxygenArmor.listener;

import fr.zyumie.oxygenArmor.OxygenArmor;
import fr.zyumie.oxygenArmor.util.ArmorItemUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Craft custom CORRIGÉ — fonctionnement :
 *
 * 1. Le joueur pose les items au sol autour du bloc trigger (rayon 1.5 bloc).
 * 2. Le joueur fait CLIC DROIT sur le bloc trigger.
 * 3. Le listener scanne les items au sol, vérifie la recette.
 * 4. Si ok → consomme les items + spawn l'armure + effets.
 *
 * C'est beaucoup plus fiable que la détection sur PlayerDropItemEvent car :
 * - Pas de timing/race condition (les items sont déjà physiquement au sol).
 * - L'action est intentionnelle (clic droit = "je veux crafter").
 */
public class CustomCraftListener implements Listener {

    private final OxygenArmor plugin;
    // Anti-spam par joueur (UUID → dernier clic en ms)
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 1000L;

    public CustomCraftListener(OxygenArmor plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // Seulement clic droit sur un bloc, main principale
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Material triggerMat = getTriggerMaterial();
        if (block.getType() != triggerMat) return;

        Player player = event.getPlayer();

        // Anti-spam
        long now = System.currentTimeMillis();
        if (now - cooldowns.getOrDefault(player.getUniqueId(), 0L) < COOLDOWN_MS) return;
        cooldowns.put(player.getUniqueId(), now);

        event.setCancelled(true); // évite d'ouvrir la table de craft

        attemptCraft(player, block);
    }

    private void attemptCraft(Player player, Block block) {
        ConfigurationSection craftCfg = plugin.getConfig().getConfigurationSection("craft");
        if (craftCfg == null) return;

        List<Map<?, ?>> ingredients = craftCfg.getMapList("ingredients");
        if (ingredients.isEmpty()) return;

        // Centre de recherche = centre du bloc + 1 bloc au-dessus (où tombent les items)
        var searchLoc = block.getLocation().add(0.5, 1.0, 0.5);
        List<Item> nearbyItems = searchLoc.getWorld()
                .getNearbyEntitiesByType(Item.class, searchLoc, 1.5)
                .stream().toList();

        // ── Vérification : tous les ingrédients présents ? ──
        for (Map<?, ?> ing : ingredients) {
            Material mat = parseMat(String.valueOf(ing.get("material")));
            int needed = ing.get("amount") instanceof Number n ? n.intValue() : 1;
            if (mat == null) continue;

            int found = nearbyItems.stream()
                    .filter(i -> i.getItemStack().getType() == mat)
                    .mapToInt(i -> i.getItemStack().getAmount())
                    .sum();

            if (found < needed) {
                send(player, cfg("messages.craft-missing"));
                return;
            }
        }

        // ── Consommation ──
        for (Map<?, ?> ing : ingredients) {
            Material mat = parseMat(String.valueOf(ing.get("material")));
            int toConsume = ing.get("amount") instanceof Number n ? n.intValue() : 1;
            if (mat == null) continue;

            for (Item itemEntity : nearbyItems) {
                if (itemEntity.getItemStack().getType() != mat || toConsume <= 0) continue;
                int stackSize = itemEntity.getItemStack().getAmount();
                if (stackSize <= toConsume) {
                    toConsume -= stackSize;
                    itemEntity.remove();
                } else {
                    itemEntity.getItemStack().setAmount(stackSize - toConsume);
                    toConsume = 0;
                }
                if (toConsume <= 0) break;
            }
        }

        // ── Résultat ──
        searchLoc.getWorld().dropItemNaturally(searchLoc, ArmorItemUtil.create(plugin, 100.0));
        send(player, cfg("messages.craft-success"));
        playEffects(searchLoc, craftCfg.getConfigurationSection("effect"));
    }

    private void playEffects(org.bukkit.Location loc, ConfigurationSection fx) {
        if (fx == null) return;
        try {
            Sound sound = Sound.valueOf(fx.getString("sound", "ENTITY_PLAYER_LEVELUP").toUpperCase());
            loc.getWorld().playSound(loc, sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {}
        try {
            Particle particle = Particle.valueOf(fx.getString("particle", "VILLAGER_HAPPY").toUpperCase());
            loc.getWorld().spawnParticle(particle, loc, 30, 0.5, 0.5, 0.5, 0.05);
        } catch (IllegalArgumentException ignored) {}
    }

    private Material getTriggerMaterial() {
        String name = plugin.getConfig().getString("craft.trigger-block", "CRAFTING_TABLE");
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("craft.trigger-block invalide: " + name + " → fallback CRAFTING_TABLE");
            return Material.CRAFTING_TABLE;
        }
    }

    private Material parseMat(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Matériau invalide dans craft.ingredients: " + name);
            return null;
        }
    }

    private String cfg(String path) {
        return plugin.getConfig().getString(path, "");
    }

    private void send(Player p, String legacy) {
        String prefix = cfg("messages.prefix");
        String mm = (prefix + legacy)
                .replace("&8","<dark_gray>").replace("&b","<aqua>")
                .replace("&7","<gray>").replace("&a","<green>")
                .replace("&c","<red>").replace("&e","<yellow>");
        p.sendMessage(MiniMessage.miniMessage().deserialize(mm));
    }
}
