package fr.zyumie.customitems.listener;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Détecte l'équipement/déséquipement des armures custom via :
 * 1. InventoryClickEvent (clic direct sur slot armure)
 * 2. PlayerInteractEvent (right-click pour équiper, dans InteractListener)
 * 3. Tâche périodique (toutes les 10 ticks) pour les cas résiduels
 */
public class ArmorListener implements Listener {

    private final CustomItems plugin;
    // UUID → dernière snapshot des armures portées (pour détecter les changements)
    private final Map<UUID, String[]> lastArmor = new HashMap<>();
    private BukkitTask checkTask;

    public ArmorListener(CustomItems plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkArmorChanges(player);
                // Tick des handlers actifs
                plugin.getGhostHandler().onArmorTick(player);
            }
        }, 0L, 10L); // toutes les 10 ticks (0.5s)
    }

    public void stopTask() {
        if (checkTask != null) checkTask.cancel();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        snapshotArmor(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        // Nettoyage des handlers actifs
        plugin.getGhostHandler().cleanup(p);
        plugin.getBerserkHandler().cleanup(p);
        plugin.getCelestialHandler().removeFromFlight(p);
        lastArmor.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (e.isCancelled()) return;

        boolean isArmorSlot = e.getSlotType() == InventoryType.SlotType.ARMOR;
        boolean isShiftMove  = e.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY;

        if (!isArmorSlot && !isShiftMove) return;

        // Délai 1 tick pour laisser l'inventaire se mettre à jour
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkArmorChanges(player), 1L);
    }

    // ── Détection des changements ─────────────────────────────────────

    private void checkArmorChanges(Player player) {
        String[] current = armorSnapshot(player);
        String[] last    = lastArmor.getOrDefault(player.getUniqueId(), new String[4]);

        for (int i = 0; i < 4; i++) {
            String prev = last[i];
            String now  = current[i];

            if (equals(prev, now)) continue; // pas de changement sur ce slot

            // Pièce retirée
            if (isCustom(prev)) onPieceUnequipped(player, prev);
            // Pièce enfilée
            if (isCustom(now))  onPieceEquipped(player, now);
        }

        lastArmor.put(player.getUniqueId(), current);
    }

    private void onPieceEquipped(Player player, String itemId) {
        // Ghost → géré par le tick périodique (GhostArmorHandler.onArmorTick)
        // Storm → géré via StormManager.immunityChecker (aucune action ici)
        // Soul, Rescue → pas d'action à l'équipement
    }

    private void onPieceUnequipped(Player player, String itemId) {
        switch (itemId) {
            case ItemRegistry.GHOST_ARMOR -> plugin.getGhostHandler().onUnequip(player);
            case ItemRegistry.SOUL_ARMOR  -> plugin.getSoulHandler().onUnequip(player);
        }
    }

    // ── Utils ─────────────────────────────────────────────────────────

    private String[] armorSnapshot(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        String[] snap = new String[4];
        for (int i = 0; i < 4; i++) {
            snap[i] = ItemBuilder.getItemId(plugin, armor[i]);
        }
        return snap;
    }

    private void snapshotArmor(Player player) {
        lastArmor.put(player.getUniqueId(), armorSnapshot(player));
    }

    private boolean isCustom(String id) { return id != null; }
    private boolean equals(String a, String b) {
        return (a == null && b == null) || (a != null && a.equals(b));
    }
}
