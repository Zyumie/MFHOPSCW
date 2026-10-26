package fr.zyumie.home.listener;

import fr.zyumie.home.Home;
import fr.zyumie.home.HomeManager;
import fr.zyumie.home.gui.HomeGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class HomeListener implements Listener {

    private final Home plugin;
    private final HomeManager manager;

    public HomeListener(Home plugin, HomeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        manager.loadPlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.unloadPlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (manager.isTeleporting(player.getUniqueId())) {
            // Vérifie si le joueur a réellement changé de bloc (ignore les mouvements de tête)
            if (e.getFrom().getBlockX() != e.getTo().getBlockX() ||
                    e.getFrom().getBlockY() != e.getTo().getBlockY() ||
                    e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {

                manager.cancelTeleport(player.getUniqueId());
                send(player, plugin.getConfig().getString("messages.tp-cancelled", "<red>Téléportation annulée (tu as bougé)."));
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player player) {
            if (manager.isTeleporting(player.getUniqueId())) {
                manager.cancelTeleport(player.getUniqueId());
                send(player, plugin.getConfig().getString("messages.tp-cancelled", "<red>Téléportation annulée (tu as subi des dégâts)."));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof HomeGui) {
            e.setCancelled(true); // Anti-dupe et anti-exploit absolu

            if (!(e.getWhoClicked() instanceof Player player)) return;
            if (e.getClickedInventory() != e.getWhoClicked().getOpenInventory().getTopInventory()) return;

            ItemStack current = e.getCurrentItem();
            if (current == null || !current.hasItemMeta()) return;

            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            NamespacedKey actionKey = new NamespacedKey(plugin, "gui_action");
            if (!meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) return;

            String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
            if (action == null) return;

            switch (action) {
                case "locked" -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    send(player, plugin.getConfig().getString("messages.no-permission", "<red>Tu n'has pas la permission."));
                }
                case "empty" -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    send(player, "<yellow>Utilise la commande <white>/sethome <nom> <yellow>pour enregistrer un home dans un emplacement libre.");
                }
                case "teleport" -> {
                    NamespacedKey homeNameKey = new NamespacedKey(plugin, "home_name");
                    String homeName = meta.getPersistentDataContainer().get(homeNameKey, PersistentDataType.STRING);
                    if (homeName == null) return;

                    if (e.isRightClick()) {
                        manager.deleteHome(player.getUniqueId(), homeName);
                        manager.savePlayer(player.getUniqueId());
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                        send(player, plugin.getConfig().getString("messages.del-success", "<green>Home supprimé.").replace("{home}", homeName));
                        new HomeGui(plugin, player).open();
                    } else if (e.isLeftClick()) {
                        player.closeInventory();
                        fr.zyumie.home.command.HomeCommands.initiateTeleport(plugin, manager, player, homeName);
                    }
                }
            }
        }
    }

    private void send(Player player, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        player.sendMessage(MiniMessage.miniMessage().deserialize(prefix + message));
    }
}