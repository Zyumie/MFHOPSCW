package fr.zyumie.oxygenArmor.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class ArmorItemUtil {

    public static final String PDC_KEY      = "oxygen_armor";
    public static final Material ARMOR_MAT  = Material.LEATHER_CHESTPLATE;

    private ArmorItemUtil() {}

    public static ItemStack create(Plugin plugin, double percent) {
        ItemStack item = new ItemStack(ARMOR_MAT);
        applyMeta(plugin, item, percent);
        return item;
    }

    public static void updatePercent(Plugin plugin, ItemStack item, double percent) {
        if (!isOxygenArmor(plugin, item)) return;
        applyMeta(plugin, item, percent);
    }

    public static double readPercent(Plugin plugin, ItemStack item) {
        if (!isOxygenArmor(plugin, item)) return -1;
        Double val = item.getItemMeta().getPersistentDataContainer()
                .get(key(plugin), PersistentDataType.DOUBLE);
        return val != null ? val : 100.0;
    }

    public static boolean isOxygenArmor(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != ARMOR_MAT || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(key(plugin), PersistentDataType.DOUBLE);
    }

    // ─────────────────────── Privé ───────────────────────

    private static void applyMeta(Plugin plugin, ItemStack item, double percent) {
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<white>Armure à Oxygène"));
        meta.setColor(colorFor(percent));
        meta.getPersistentDataContainer()
                .set(key(plugin), PersistentDataType.DOUBLE, Math.max(0, Math.min(100, percent)));
        item.setItemMeta(meta);
    }

    /** Interpolation linéaire Blanc (100%) → Noir (0%) */
    private static Color colorFor(double percent) {
        int v = (int) Math.round(Math.max(0, Math.min(100, percent)) / 100.0 * 255);
        return Color.fromRGB(v, v, v);
    }

    private static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, PDC_KEY);
    }
}
