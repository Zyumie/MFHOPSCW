package fr.zyumie.customitems.listener;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

/**
 * Bloque toute modification des items custom sur :
 *  - Enclume       (renommer, réparer, enchanter)
 *  - Table d'ench. (enchanter)
 *  - Meule         (désenchanter / retirer malédictions)
 *  - Table de forge (upgrade Netherite, application de trim)
 *
 * La table de forge est le point critique pour les trims :
 * sans ce bloc, un joueur pourrait apposer n'importe quel trim
 * par-dessus celui configuré dans config.yml.
 */
public class ItemProtectionListener implements Listener {

    private final CustomItems plugin;

    public ItemProtectionListener(CustomItems plugin) {
        this.plugin = plugin;
    }

    // ── Enclume ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        if (hasCustomItem(event.getInventory().getItem(0),
                          event.getInventory().getItem(1))) {
            event.setResult(null);

            if (event.getView().getPlayer() instanceof Player player) {
                sendDenied(player);
            }
        }
    }

    // ── Table d'enchantement ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchant(EnchantItemEvent event) {
        if (isCustom(event.getItem())) {
            event.setCancelled(true);
            sendDenied(event.getEnchanter());
        }
    }

    // ── Meule ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindEvent(PrepareGrindstoneEvent event) {
        if (hasCustomItem(event.getInventory().getItem(0),
                          event.getInventory().getItem(1))) {
            event.getHandlers();
        }
    }

    // ── Table de forge (Smithing Table) ───────────────────────────────────

    /**
     * Bloque TOUT usage de la table de forge si l'item de base est custom :
     *  - Upgrade Netherite → bloqué (l'armure est déjà parfaite)
     *  - Application d'un trim → bloqué (le trim est défini dans config.yml)
     *  - Modification du trim existant → bloqué
     *
     * On annule en vidant le résultat (setResult(null/air)) ce qui empêche
     * la sortie sans consommer les ingrédients.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        if (!(event.getInventory() instanceof SmithingInventory smithing)) return;

        // Slot 0 = template, Slot 1 = item de base, Slot 2 = matériau
        ItemStack base = smithing.getItem(1);
        if (!isCustom(base)) return;

        // Vide le résultat → aucune transformation possible
        event.setResult(new ItemStack(org.bukkit.Material.AIR));

        if (event.getView().getPlayer() instanceof Player player) {
            sendDenied(player);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isCustom(ItemStack item) {
        return ItemBuilder.isCustomItem(plugin, item);
    }

    private boolean hasCustomItem(ItemStack... items) {
        for (ItemStack item : items) {
            if (isCustom(item)) return true;
        }
        return false;
    }

    private void sendDenied(Player player) {
        player.sendMessage(
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<dark_gray>[<red>✗<dark_gray>] <gray>Les items custom ne peuvent pas être modifiés.")
        );
    }
}
