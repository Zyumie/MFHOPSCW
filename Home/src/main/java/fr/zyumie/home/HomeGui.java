package fr.zyumie.home.gui;

import fr.zyumie.home.Home;
import fr.zyumie.home.model.HomeData;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class HomeGui implements InventoryHolder {

    private final Home plugin;
    private final Player player;
    private final Inventory inventory;

    public HomeGui(Home plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        String title = plugin.getConfig().getString("messages.gui-title", "<dark_gray>Gestion de vos Homes");
        // Inventaire de 9 slots (une seule ligne, celle du haut)
        this.inventory = Bukkit.createInventory(this, 9, MiniMessage.miniMessage().deserialize(title));
        loadItems();
    }

    private void loadItems() {
        // Remplir les côtés avec des vitres décoratives grises (slots 0, 1, 7, 8)
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(MiniMessage.miniMessage().deserialize(" "));
            filler.setItemMeta(fillerMeta);
        }
        for (int slot = 0; slot < 9; slot++) {
            if (slot < 2 || slot > 6) {
                inventory.setItem(slot, filler);
            }
        }

        Map<String, HomeData> playerHomes = plugin.getHomeManager().getHomes(player.getUniqueId());
        int maxHomes = plugin.getHomeManager().getMaxHomes(player);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

        List<Map.Entry<String, HomeData>> homesList = playerHomes.entrySet().stream().toList();

        NamespacedKey actionKey = new NamespacedKey(plugin, "gui_action");
        NamespacedKey homeNameKey = new NamespacedKey(plugin, "home_name");

        // Les 5 emplacements centraux correspondent aux 5 homes possibles (slots 2, 3, 4, 5, 6)
        for (int i = 0; i < 5; i++) {
            int guiSlot = 2 + i;
            int homeIndex = i + 1;

            ItemStack item;
            ItemMeta meta;

            if (homeIndex > maxHomes) {
                // Slot verrouillé : le joueur n'a pas la permission pour ce home
                item = new ItemStack(Material.BARRIER);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(MiniMessage.miniMessage().deserialize("<red><b>Home #" + homeIndex + " (Verrouillé)</b>"));
                    meta.lore(List.of(
                            MiniMessage.miniMessage().deserialize("<gray>Nécessite la permission: <yellow>home.max." + homeIndex),
                            MiniMessage.miniMessage().deserialize("<red>➤ Accès refusé")
                    ));
                    meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "locked");
                    item.setItemMeta(meta);
                }
            } else if (i < homesList.size()) {
                // Home existant
                Map.Entry<String, HomeData> entry = homesList.get(i);
                String homeName = entry.getKey();
                HomeData hd = entry.getValue();

                item = new ItemStack(Material.RED_BED);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(MiniMessage.miniMessage().deserialize("<aqua><b>Home: <yellow>" + homeName + "</b>"));
                    meta.lore(List.of(
                            MiniMessage.miniMessage().deserialize("<gray>Monde: <white>" + (hd.getLocation().getWorld() != null ? hd.getLocation().getWorld().getName() : "Inconnu")),
                            MiniMessage.miniMessage().deserialize(String.format("<gray>X: <white>%.1f <gray>Y: <white>%.1f <gray>Z: <white>%.1f",
                                    hd.getLocation().getX(), hd.getLocation().getY(), hd.getLocation().getZ())),
                            MiniMessage.miniMessage().deserialize("<gray>Créé le: <white>" + sdf.format(new Date(hd.getSetTime()))),
                            MiniMessage.miniMessage().deserialize(""),
                            MiniMessage.miniMessage().deserialize("<green>➤ Clic gauche pour se téléporter"),
                            MiniMessage.miniMessage().deserialize("<red>➤ Clic droit pour supprimer")
                    ));
                    meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "teleport");
                    meta.getPersistentDataContainer().set(homeNameKey, PersistentDataType.STRING, homeName);
                    item.setItemMeta(meta);
                }
            } else {
                // Emplacement libre
                item = new ItemStack(Material.LIME_DYE);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.displayName(MiniMessage.miniMessage().deserialize("<green><b>Emplacement libre #" + homeIndex + "</b>"));
                    meta.lore(List.of(
                            MiniMessage.miniMessage().deserialize("<gray>Utilise <yellow>/sethome <nom>"),
                            MiniMessage.miniMessage().deserialize("<gray>pour définir un home ici.")
                    ));
                    meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "empty");
                    item.setItemMeta(meta);
                }
            }
            inventory.setItem(guiSlot, item);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }
}