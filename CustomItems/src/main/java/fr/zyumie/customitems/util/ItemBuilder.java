package fr.zyumie.customitems.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class ItemBuilder {

    public static final String PDC_KEY = "custom_item";

    private ItemBuilder() {}

    public static ItemStack build(Plugin plugin, ConfigurationSection section,
                                  String slot, String itemId,
                                  String nameOverride, List<String> loreOverride) {
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
        if (!rawLore.isEmpty()) meta.lore(rawLore.stream().map(ItemBuilder::mm).toList());

        // CMD
        int cmd = slot != null ? section.getInt(slot + ".custom-model-data", 0)
                               : section.getInt("custom-model-data", 0);
        if (cmd > 0) meta.setCustomModelData(cmd);

        // Couleur leather
        if (slot != null && meta instanceof LeatherArmorMeta leatherMeta) {
            String colorHex = section.getString(slot + ".color");
            if (colorHex != null && !colorHex.equalsIgnoreCase("null")) {
                try {
                    int c = (int) Long.parseLong(colorHex.replace("0x","").replace("#",""), 16);
                    leatherMeta.setColor(Color.fromRGB(c));
                } catch (NumberFormatException ignored) {}
            }
        }

        // ── Armor Trim ────────────────────────────────────────────────────
        // Priorité : trim par slot > trim global du set
        if (slot != null && meta instanceof ArmorMeta armorMeta) {
            ConfigurationSection trimSec = section.getConfigurationSection(slot + ".trim");
            if (trimSec == null) trimSec = section.getConfigurationSection("trim");
            if (trimSec != null && trimSec.getBoolean("enabled", false)) {
                applyTrim(armorMeta, trimSec);
            }
        }

        // Enchantements
        ConfigurationSection enchSec = section.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String name : enchSec.getKeys(false)) {
                try {
                    Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase()));
                    if (ench != null) meta.addEnchant(ench, enchSec.getInt(name), true);
                } catch (Exception ignored) {}
            }
        }

        // Flags
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                          ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ARMOR_TRIM);

        // PDC
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, PDC_KEY), PersistentDataType.STRING, itemId);

        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack buildWeapon(Plugin plugin, ConfigurationSection section, String itemId) {
        return build(plugin, section, null, itemId, null, null);
    }

    public static ItemStack buildArmorPiece(Plugin plugin, ConfigurationSection section,
                                            String slot, String itemId) {
        return build(plugin, section, slot, itemId, null, null);
    }

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

    // ── Armor Trim ────────────────────────────────────────────────────────

    private static void applyTrim(ArmorMeta meta, ConfigurationSection trimSec) {
        String rawMat = trimSec.getString("material", "").toLowerCase().replace("minecraft:", "");
        String rawPat = trimSec.getString("pattern",  "").toLowerCase().replace("minecraft:", "");
        if (rawMat.isEmpty() || rawPat.isEmpty()) return;

        TrimMaterial trimMaterial = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(rawMat));
        TrimPattern  trimPattern  = Registry.TRIM_PATTERN .get(NamespacedKey.minecraft(rawPat));
        if (trimMaterial == null || trimPattern == null) return;

        meta.setTrim(new ArmorTrim(trimMaterial, trimPattern));
    }

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
