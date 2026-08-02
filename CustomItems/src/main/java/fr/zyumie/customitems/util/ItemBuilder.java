package fr.zyumie.customitems.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilitaire de construction d'items custom.
 * Chaque item est tagué via PDC : key "custom_item" → identifiant string unique.
 */
public final class ItemBuilder {

    /** Clé PDC commune à tous les items custom du plugin */
    public static final String PDC_KEY = "custom_item";

    private ItemBuilder() {}

    /**
     * Construit un ItemStack custom depuis une section de config.
     *
     * @param plugin    plugin propriétaire (pour la NamespacedKey)
     * @param section   section YAML de l'item (ex: config.getConfigurationSection("armors.storm"))
     * @param slot      "helmet" | "chestplate" | "leggings" | "boots" | null (pour les armes)
     * @param itemId    identifiant unique PDC (ex: "storm_armor", "celestial_sword")
     * @param nameOverride  nom affiché — null = utilise section.display-name
     * @param loreOverride  lore — null = utilise section.lore
     */
    public static ItemStack build(Plugin plugin, ConfigurationSection section,
                                  String slot, String itemId,
                                  String nameOverride, List<String> loreOverride) {
        // Matériau
        String matKey = slot != null
                ? section.getString(slot + ".material", "STONE")
                : section.getString("material", "STONE");
        Material material;
        try { material = Material.valueOf(matKey.toUpperCase()); }
        catch (IllegalArgumentException e) { material = Material.STONE; }

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        // Nom
        String rawName = nameOverride != null ? nameOverride : section.getString("display-name", itemId);
        meta.displayName(mm(rawName));

        // Lore
        List<String> rawLore = loreOverride != null ? loreOverride : section.getStringList("lore");
        if (!rawLore.isEmpty()) {
            meta.lore(rawLore.stream().map(ItemBuilder::mm).toList());
        }

        // Custom Model Data
        int cmdKey = slot != null
                ? section.getInt(slot + ".custom-model-data", 0)
                : section.getInt("custom-model-data", 0);
        if (cmdKey > 0) meta.setCustomModelData(cmdKey);

        // Couleur pour leather armor
        if (slot != null && meta instanceof LeatherArmorMeta leatherMeta) {
            String colorHex = section.getString(slot + ".color");
            if (colorHex != null && !colorHex.equalsIgnoreCase("null")) {
                try {
                    int colorInt = (int) Long.parseLong(colorHex.replace("0x", "").replace("#", ""), 16);
                    leatherMeta.setColor(Color.fromRGB(colorInt));
                } catch (NumberFormatException ignored) {}
            }
        }

        // Enchantements
        ConfigurationSection enchSec = section.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String enchName : enchSec.getKeys(false)) {
                try {
                    Enchantment ench = org.bukkit.Registry.ENCHANTMENT.get(
                            NamespacedKey.minecraft(enchName.toLowerCase()));
                    if (ench != null) {
                        meta.addEnchant(ench, enchSec.getInt(enchName), true);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Flags d'affichage
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                          ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        // Tag PDC — identifiant unique de l'item
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, PDC_KEY),
                PersistentDataType.STRING,
                itemId);

        stack.setItemMeta(meta);
        return stack;
    }

    /** Version simplifiée pour les armes (pas de slot) */
    public static ItemStack buildWeapon(Plugin plugin, ConfigurationSection section, String itemId) {
        return build(plugin, section, null, itemId, null, null);
    }

    /** Version pour une pièce d'armure */
    public static ItemStack buildArmorPiece(Plugin plugin, ConfigurationSection section,
                                            String slot, String itemId) {
        return build(plugin, section, slot, itemId, null, null);
    }

    /**
     * Lit l'identifiant PDC d'un item.
     * @return l'identifiant, ou null si ce n'est pas un item custom
     */
    public static String getItemId(Plugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, PDC_KEY), PersistentDataType.STRING);
    }

    public static boolean isCustomItem(Plugin plugin, ItemStack item, String expectedId) {
        return expectedId.equals(getItemId(plugin, item));
    }

    public static boolean isCustomItem(Plugin plugin, ItemStack item) {
        return getItemId(plugin, item) != null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static Component mm(String legacy) {
        return MiniMessage.miniMessage().deserialize(
                legacy.replace("&0","<black>").replace("&1","<dark_blue>")
                      .replace("&2","<dark_green>").replace("&3","<dark_aqua>")
                      .replace("&4","<dark_red>").replace("&5","<dark_purple>")
                      .replace("&6","<gold>").replace("&7","<gray>")
                      .replace("&8","<dark_gray>").replace("&9","<blue>")
                      .replace("&a","<green>").replace("&b","<aqua>")
                      .replace("&c","<red>").replace("&d","<light_purple>")
                      .replace("&e","<yellow>").replace("&f","<white>")
                      .replace("&l","<bold>").replace("&o","<italic>")
                      .replace("&n","<underlined>").replace("&m","<strikethrough>")
                      .replace("&r","<reset>"));
    }
}
