package fr.zyumie.profile.gui;

import fr.zyumie.profile.ProfileData;
import fr.zyumie.profile.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Construit et affiche le GUI de /profile décrit dans gui.yml.
 * Layout, matériaux, noms et lores sont 100% configurables ; seule la tête (type: HEAD) a un
 * rendu spécial (skin du joueur ciblé via SkullMeta#setOwningPlayer).
 */
public class ProfileGuiManager {

    private final JavaPlugin plugin;
    private FileConfiguration guiConfig;

    public ProfileGuiManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /** Recharge gui.yml depuis le disque (le crée depuis les ressources du jar s'il n'existe pas encore). */
    public void reload() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void open(Player viewer, ProfileData data) {
        int rows = Math.max(1, Math.min(6, guiConfig.getInt("rows", 3)));
        Component title = Msg.parse(placeholders(guiConfig.getString("title", "Profil"), data));

        ProfileGuiHolder holder = new ProfileGuiHolder(data.target().getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inv);

        applyFiller(inv);
        applyItems(inv, data);

        viewer.openInventory(inv);
    }

    private void applyFiller(Inventory inv) {
        ConfigurationSection filler = guiConfig.getConfigurationSection("filler");
        if (filler == null) return;

        String materialName = filler.getString("material", "NONE");
        if (materialName == null || materialName.equalsIgnoreCase("NONE")) return;

        ItemStack fillerItem = new ItemStack(parseMaterial(materialName, Material.GRAY_STAINED_GLASS_PANE));
        ItemMeta meta = fillerItem.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.parse(filler.getString("name", " ")));
            fillerItem.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, fillerItem);
        }
    }

    private void applyItems(Inventory inv, ProfileData data) {
        ConfigurationSection items = guiConfig.getConfigurationSection("items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null) continue;

            int slot = item.getInt("slot", -1);
            if (slot < 0 || slot >= inv.getSize()) continue;

            String type = item.getString("type", "NONE").toUpperCase();
            ItemStack stack = type.equals("HEAD")
                    ? buildHead(data.target())
                    : new ItemStack(parseMaterial(item.getString("material", "STONE"), Material.STONE));

            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String name = item.getString("name");
                if (name != null) {
                    meta.displayName(Msg.parse(placeholders(name, data)));
                }

                List<String> loreLines = item.getStringList("lore");
                if (!loreLines.isEmpty()) {
                    List<Component> lore = new ArrayList<>();
                    for (String line : loreLines) {
                        lore.add(Msg.parse(placeholders(line, data)));
                    }
                    meta.lore(lore);
                }
                stack.setItemMeta(meta);
            }
            inv.setItem(slot, stack);
        }
    }

    private ItemStack buildHead(OfflinePlayer target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name);
        return m != null ? m : fallback;
    }

    /**
     * Remplace les placeholders {player} {status} {money} {faction} {bank}.
     * Les valeurs qui peuvent contenir du texte non fiable (nom de joueur, nom de faction) sont
     * échappées (MiniMessage#escapeTags) avant insertion, pour qu'un nom de faction ne puisse pas
     * injecter de balises MiniMessage dans le GUI affiché à un tiers.
     */
    private String placeholders(String text, ProfileData data) {
        if (text == null) return "";
        String name = data.target().getName() != null ? data.target().getName() : "Inconnu";
        String status = plugin.getConfig().getString(
                data.online() ? "messages.status-online" : "messages.status-offline",
                data.online() ? "En ligne" : "Hors ligne");

        return text
                .replace("{player}", Msg.escape(name))
                .replace("{status}", Msg.escape(status))
                .replace("{money}", Msg.escape(data.money()))
                .replace("{faction}", Msg.escape(data.faction()))
                .replace("{bank}", Msg.escape(data.bank()));
    }
}
