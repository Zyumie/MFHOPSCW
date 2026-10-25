package fr.zyumie.customitems.listener;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

/**
 * Gère les restrictions de modification sur les items custom :
 * - Table d'enchantement : Autorisée (les joueurs peuvent enchanter).
 * - Enclume : Autorisée pour appliquer des enchantements (livres).
 * - Meule : Bloquée pour éviter de désenchanter ou altérer l'item.
 * - Table de forge : Bloquée pour préserver le trim et les specs d'origine configurés.
 */
public class ItemProtectionListener implements Listener {

    private final CustomItems plugin;

    public ItemProtectionListener(CustomItems plugin) {
        this.plugin = plugin;
    }

    // ── Enclume (Anvil) ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        // Optionnel : Vous pouvez laisser l'enclume libre pour permettre
        // l'application de livres enchantés sur vos items custom.
        // Si vous souhaitez tout bloquer sauf les livres, vous pouvez affiner ici.
    }

    // ── Table d'enchantement (EnchantItemEvent supprimé) ──────────────────
    // Les joueurs peuvent désormais placer librement les items custom sur la table d'enchantement.

    // ── Meule (Grindstone) ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindEvent(PrepareGrindstoneEvent event) {
        if (hasCustomItem(event.getInventory().getItem(0),
                event.getInventory().getItem(1))) {
            // Empêche de placer l'item dans la meule pour le désenchanter
            event.setResult(null);
            if (event.getView().getPlayer() instanceof Player player) {
                sendDenied(player);
            }
        }
    }

    // ── Table de forge (Smithing Table) ───────────────────────────────────

    /**
     * Bloque l'usage de la table de forge si l'item de base est custom
     * pour protéger le Trim et les configurations de base (config.yml).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        if (!(event.getInventory() instanceof SmithingInventory smithing)) return;

        ItemStack base = smithing.getItem(1);
        if (!isCustom(base)) return;

        // Vide le résultat → empêche de modifier le trim ou d'appliquer des upgrades non désirés
        event.setResult(new ItemStack(Material.AIR));

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
                        .deserialize("<dark_gray>[<red>✗<dark_gray>] <gray>Cette action n'est pas autorisée sur cet item custom.")
        );
    }
}