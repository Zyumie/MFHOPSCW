package fr.zyumie.oxygenArmor.listener;

import fr.zyumie.oxygenArmor.ArmorManager;
import fr.zyumie.oxygenArmor.OxygenArmor;
import fr.zyumie.oxygenArmor.util.ArmorItemUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Détecte l'équipement / retrait de l'armure à oxygène.
 * PlayerArmorChangeEvent n'existe pas en Paper 1.21.x → on utilise
 * InventoryClickEvent + PlayerInteractEvent (right-click pour équiper),
 * avec un check 1 tick plus tard pour laisser l'inventaire se mettre à jour.
 */
public class ArmorEquipListener implements Listener {

    private final OxygenArmor plugin;
    private final ArmorManager manager;
    // Dernier état connu: UUID → portait l'armure O2 ?
    private final Map<UUID, Boolean> lastState = new HashMap<>();

    public ArmorEquipListener(OxygenArmor plugin, ArmorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ── Connexion / Déconnexion ──────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        manager.loadPlayer(p);

        // Sync % depuis PDC si armure déjà portée à la reconnexion
        ItemStack chest = p.getInventory().getChestplate();
        if (ArmorItemUtil.isOxygenArmor(plugin, chest)) {
            manager.getData(p).setReservePercent(ArmorItemUtil.readPercent(plugin, chest));
        }
        lastState.put(p.getUniqueId(), manager.isWearingArmor(p));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        // Sauvegarde le % dans le PDC de l'item avant de décharger
        ItemStack chest = p.getInventory().getChestplate();
        if (ArmorItemUtil.isOxygenArmor(plugin, chest)) {
            ArmorItemUtil.updatePercent(plugin, chest, manager.getReserve(p));
            p.getInventory().setChestplate(chest);
        }
        manager.unloadPlayer(p);
        lastState.remove(p.getUniqueId());
    }

    // ── Clic dans l'inventaire ───────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        boolean relevant = false;

        // Clic direct sur un slot armure
        if (e.getSlotType() == InventoryType.SlotType.ARMOR) {
            relevant = true;
        }
        // Shift-clic depuis un slot normal → peut envoyer l'item en slot armure
        else if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack clicked = e.getCurrentItem();
            if (ArmorItemUtil.isOxygenArmor(plugin, clicked)) relevant = true;
        }

        if (relevant) {
            scheduleCheck(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        // Si le drag implique un slot armure (slot 5 = helmet, 6 = chest, 7 = legs, 8 = boots)
        for (int slot : e.getRawSlots()) {
            if (slot >= 5 && slot <= 8) {
                scheduleCheck(player);
                return;
            }
        }
    }

    // ── Right-click pour équiper rapidement ─────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        Player p = e.getPlayer();
        ItemStack inHand = p.getInventory().getItemInMainHand();
        if (ArmorItemUtil.isOxygenArmor(plugin, inHand)) {
            scheduleCheck(p);
        }
    }

    // ── Logique ─────────────────────────────────────────────────────────

    /**
     * Planifie un check 1 tick après l'event pour laisser Bukkit
     * appliquer le changement d'inventaire avant qu'on lise l'état.
     */
    private void scheduleCheck(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            checkStateChange(player);
        }, 1L);
    }

    private void checkStateChange(Player player) {
        boolean wearing = manager.isWearingArmor(player);
        boolean was = lastState.getOrDefault(player.getUniqueId(), false);

        if (wearing == was) return; // pas de changement
        lastState.put(player.getUniqueId(), wearing);

        if (wearing) {
            // Vient d'être enfilée : sync % depuis PDC
            ItemStack chest = player.getInventory().getChestplate();
            double pct = ArmorItemUtil.readPercent(plugin, chest);
            manager.getData(player).setReservePercent(pct);
            send(player, cfg("messages.armor-equipped").replace("{percent}", String.format("%.0f", pct)));
        } else {
            // Vient d'être retirée : sauvegarde % dans PDC de l'item
            // (l'item est maintenant dans la main ou l'inventaire — on cherche où)
            double pct = manager.getReserve(player);
            savePercentToInventory(player, pct);
            send(player, cfg("messages.armor-removed"));
        }
    }

    /** Trouve l'armure O2 dans l'inventaire du joueur et met à jour son PDC. */
    private void savePercentToInventory(Player player, double pct) {
        var inv = player.getInventory();
        // Vérifie chestplate d'abord (cas rare où l'event a pas encore swap)
        if (ArmorItemUtil.isOxygenArmor(plugin, inv.getChestplate())) {
            ArmorItemUtil.updatePercent(plugin, inv.getChestplate(), pct);
            return;
        }
        // Cherche dans le reste de l'inventaire
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (ArmorItemUtil.isOxygenArmor(plugin, item)) {
                ArmorItemUtil.updatePercent(plugin, item, pct);
                inv.setItem(i, item);
                return;
            }
        }
        // Main
        if (ArmorItemUtil.isOxygenArmor(plugin, inv.getItemInMainHand())) {
            ArmorItemUtil.updatePercent(plugin, inv.getItemInMainHand(), pct);
        }
    }

    private String cfg(String path) { return plugin.getConfig().getString(path, ""); }

    private void send(Player p, String message) {
        String prefix = cfg("messages.prefix");
        p.sendMessage(MiniMessage.miniMessage().deserialize(prefix + message));
    }
}
