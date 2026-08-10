package fr.zyumie.customitems;

import fr.zyumie.customitems.util.ItemBuilder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registre central de tous les items custom.
 * Fabrique les ItemStacks et expose les ID attendus.
 *
 * Modifications v0.5.1 :
 *  - TOOLTIP_STYLE appliqué sur chaque item (conservé depuis ta version)
 *  - Armor Trim lu depuis config.yml et appliqué via ItemBuilder
 *  - DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP supprimé du constructeur
 *    (géré ici directement pour plus de contrôle)
 */
public class ItemRegistry {

    // ── IDs PDC ──────────────────────────────────────────────────────────
    public static final String STORM_ARMOR     = "storm_armor";
    public static final String GHOST_ARMOR     = "ghost_armor";
    public static final String RESCUE_ARMOR    = "rescue_armor";
    public static final String SOUL_ARMOR      = "soul_armor";

    public static final String CELESTIAL_SWORD = "celestial_sword";
    public static final String CELESTIAL_MACE  = "celestial_mace";
    public static final String EXPLOSIVE_BOW   = "explosive_bow";
    public static final String ENDER_BOW       = "ender_bow";
    public static final String BERSERK_SWORD   = "berserk_sword";

    public static final String[] ARMOR_SLOTS = {"helmet", "chestplate", "leggings", "boots"};

    private final JavaPlugin plugin;

    private static final Map<String, String> GIVE_NAMES = new HashMap<>();
    static {
        GIVE_NAMES.put("storm_armor",     STORM_ARMOR);
        GIVE_NAMES.put("storm",           STORM_ARMOR);
        GIVE_NAMES.put("ghost_armor",     GHOST_ARMOR);
        GIVE_NAMES.put("ghost",           GHOST_ARMOR);
        GIVE_NAMES.put("rescue_armor",    RESCUE_ARMOR);
        GIVE_NAMES.put("rescue",          RESCUE_ARMOR);
        GIVE_NAMES.put("soul_armor",      SOUL_ARMOR);
        GIVE_NAMES.put("soul",            SOUL_ARMOR);
        GIVE_NAMES.put("celestial_sword", CELESTIAL_SWORD);
        GIVE_NAMES.put("celestial",       CELESTIAL_SWORD);
        GIVE_NAMES.put("explosive_bow",   EXPLOSIVE_BOW);
        GIVE_NAMES.put("explosive",       EXPLOSIVE_BOW);
        GIVE_NAMES.put("ender_bow",       ENDER_BOW);
        GIVE_NAMES.put("ender",           ENDER_BOW);
        GIVE_NAMES.put("berserk_sword",   BERSERK_SWORD);
        GIVE_NAMES.put("berserk",         BERSERK_SWORD);
    }

    public ItemRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Armures ───────────────────────────────────────────────────────────

    public ItemStack[] buildArmorSet(String armorKey, String itemId) {
        ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("armors." + armorKey);

        if (sec == null) {
            return new ItemStack[4];
        }

        ItemStack[] pieces = new ItemStack[4];
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {

            // Construit la pièce (avec trim intégré dans ItemBuilder)
            pieces[i] = ItemBuilder.buildArmorPiece(plugin, sec, ARMOR_SLOTS[i], itemId);

            if (pieces[i] == null) {
                continue;
            }

            // ─────────────────────────────────────────────
            // Tooltip personnalisé de l'armure
            //
            // Exemple pour Ghost :
            // customitems:ghost/ghost
            // ─────────────────────────────────────────────

            pieces[i].setData(
                DataComponentTypes.TOOLTIP_STYLE,
                Key.key("customitems", armorKey + "/" + armorKey)
            );
        }
        return pieces;
    }

    // ── Armes (ta version conservée intégralement) ────────────────────────

    public ItemStack buildCelestialSword() {
        var sec = plugin.getConfig().getConfigurationSection("weapons.celestial-sword");
        if (sec == null) return null;
        ItemStack item = ItemBuilder.buildWeapon(plugin, sec, CELESTIAL_SWORD);
        item.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key("minecraft", "celestialsword"));
        return item;
    }

    public ItemStack buildCelestialMace() {
        var sec = plugin.getConfig().getConfigurationSection("weapons.celestial-sword");
        if (sec == null) return null;
        ItemStack item = ItemBuilder.build(plugin, new MaceFakeCfg(sec), null, CELESTIAL_MACE,
                sec.getString("mace-display-name", "&e&l⚒ Masse Céleste"),
                sec.getStringList("mace-lore"));
        item.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key("minecraft", "celestialmace"));
        return item;
    }

    public ItemStack buildExplosiveBow() {
        var sec = plugin.getConfig().getConfigurationSection("weapons.explosive-bow");
        if (sec == null) return null;
        ItemStack item = ItemBuilder.buildWeapon(plugin, sec, EXPLOSIVE_BOW);
        item.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key("minecraft", "explosivebow"));
        return item;
    }

    public ItemStack buildEnderBow() {
        var sec = plugin.getConfig().getConfigurationSection("weapons.ender-bow");
        if (sec == null) return null;
        ItemStack item = ItemBuilder.buildWeapon(plugin, sec, ENDER_BOW);
        item.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key("minecraft", "enderbow"));
        return item;
    }

    public ItemStack buildBerserkSword() {
        var sec = plugin.getConfig().getConfigurationSection("weapons.berserk-sword");
        if (sec == null) return null;
        ItemStack item = ItemBuilder.buildWeapon(plugin, sec, BERSERK_SWORD);
        item.setData(DataComponentTypes.TOOLTIP_STYLE, Key.key("minecraft", "berserksword"));
        return item;
    }

    // ── Résolution /items give ────────────────────────────────────────────

    public ItemStack[] resolveGiveItems(String name) {
        String id = GIVE_NAMES.get(name.toLowerCase());
        if (id == null) return null;
        return switch (id) {
            case STORM_ARMOR     -> buildArmorSet("storm",   STORM_ARMOR);
            case GHOST_ARMOR     -> buildArmorSet("ghost",   GHOST_ARMOR);
            case RESCUE_ARMOR    -> buildArmorSet("rescue",  RESCUE_ARMOR);
            case SOUL_ARMOR      -> buildArmorSet("soul",    SOUL_ARMOR);
            case CELESTIAL_SWORD -> new ItemStack[]{ buildCelestialSword() };
            case EXPLOSIVE_BOW   -> new ItemStack[]{ buildExplosiveBow() };
            case ENDER_BOW       -> new ItemStack[]{ buildEnderBow() };
            case BERSERK_SWORD   -> new ItemStack[]{ buildBerserkSword() };
            default -> null;
        };
    }

    public Set<String> getGiveNames() { return GIVE_NAMES.keySet(); }

    public boolean isArmorId(String id) {
        return id != null && (id.equals(STORM_ARMOR) || id.equals(GHOST_ARMOR)
                || id.equals(RESCUE_ARMOR) || id.equals(SOUL_ARMOR));
    }

    public boolean isWeaponId(String id) {
        return id != null && (id.equals(CELESTIAL_SWORD) || id.equals(CELESTIAL_MACE)
                || id.equals(EXPLOSIVE_BOW) || id.equals(ENDER_BOW) || id.equals(BERSERK_SWORD));
    }

    // ── MaceFakeCfg (inchangé) ────────────────────────────────────────────

    private static class MaceFakeCfg implements ConfigurationSection {
        private final ConfigurationSection parent;
        MaceFakeCfg(ConfigurationSection parent) { this.parent = parent; }

        @Override public String getString(String path, String def) {
            if ("material".equals(path)) return parent.getString("mace-material", "MACE");
            if ("custom-model-data".equals(path)) return String.valueOf(parent.getInt("mace-custom-model-data", 0));
            return parent.getString(path, def);
        }
        @Override public int getInt(String path, int def) {
            if ("custom-model-data".equals(path)) return parent.getInt("mace-custom-model-data", def);
            return parent.getInt(path, def);
        }
        @Override public org.bukkit.configuration.ConfigurationSection getConfigurationSection(String p) { return parent.getConfigurationSection(p); }
        @Override public java.util.Set<String> getKeys(boolean deep) { return parent.getKeys(deep); }
        @Override public java.util.Map<String,Object> getValues(boolean deep) { return parent.getValues(deep); }
        @Override public boolean contains(String p) { return parent.contains(p); }
        @Override public boolean contains(String p, boolean ig) { return parent.contains(p, ig); }
        @Override public boolean isSet(String p) { return parent.isSet(p); }
        @Override public String getCurrentPath() { return parent.getCurrentPath(); }
        @Override public String getName() { return parent.getName(); }
        @Override public org.bukkit.configuration.Configuration getRoot() { return parent.getRoot(); }
        @Override public org.bukkit.configuration.ConfigurationSection getParent() { return parent.getParent(); }
        @Override public Object get(String p) { return parent.get(p); }
        @Override public Object get(String p, Object d) { return parent.get(p, d); }
        @Override public void set(String p, Object v) { parent.set(p, v); }
        @Override public org.bukkit.configuration.ConfigurationSection createSection(String p) { return parent.createSection(p); }
        @Override public org.bukkit.configuration.ConfigurationSection createSection(String p, java.util.Map<?,?> m) { return parent.createSection(p, m); }
        @Override public String getString(String p) { return getString(p, null); }
        @Override public boolean isString(String p) { return parent.isString(p); }
        @Override public int getInt(String p) { return getInt(p, 0); }
        @Override public boolean isInt(String p) { return parent.isInt(p); }
        @Override public boolean getBoolean(String p) { return parent.getBoolean(p); }
        @Override public boolean getBoolean(String p, boolean d) { return parent.getBoolean(p, d); }
        @Override public boolean isBoolean(String p) { return parent.isBoolean(p); }
        @Override public double getDouble(String p) { return parent.getDouble(p); }
        @Override public double getDouble(String p, double d) { return parent.getDouble(p, d); }
        @Override public boolean isDouble(String p) { return parent.isDouble(p); }
        @Override public long getLong(String p) { return parent.getLong(p); }
        @Override public long getLong(String p, long d) { return parent.getLong(p, d); }
        @Override public boolean isLong(String p) { return parent.isLong(p); }
        @Override public java.util.List<?> getList(String p) { return parent.getList(p); }
        @Override public java.util.List<?> getList(String p, java.util.List<?> d) { return parent.getList(p, d); }
        @Override public boolean isList(String p) { return parent.isList(p); }
        @Override public java.util.List<String> getStringList(String p) { return parent.getStringList(p); }
        @Override public java.util.List<Integer> getIntegerList(String p) { return parent.getIntegerList(p); }
        @Override public java.util.List<Boolean> getBooleanList(String p) { return parent.getBooleanList(p); }
        @Override public java.util.List<Double> getDoubleList(String p) { return parent.getDoubleList(p); }
        @Override public java.util.List<Float> getFloatList(String p) { return parent.getFloatList(p); }
        @Override public java.util.List<Long> getLongList(String p) { return parent.getLongList(p); }
        @Override public java.util.List<Byte> getByteList(String p) { return parent.getByteList(p); }
        @Override public java.util.List<Character> getCharacterList(String p) { return parent.getCharacterList(p); }
        @Override public java.util.List<Short> getShortList(String p) { return parent.getShortList(p); }
        @Override public java.util.List<java.util.Map<?,?>> getMapList(String p) { return parent.getMapList(p); }
        @Override public <T> T getObject(String p, Class<T> c) { return parent.getObject(p, c); }
        @Override public <T> T getObject(String p, Class<T> c, T d) { return parent.getObject(p, c, d); }
        @Override public <T extends org.bukkit.configuration.serialization.ConfigurationSerializable> T getSerializable(String p, Class<T> c) { return parent.getSerializable(p, c); }
        @Override public <T extends org.bukkit.configuration.serialization.ConfigurationSerializable> T getSerializable(String p, Class<T> c, T d) { return parent.getSerializable(p, c, d); }
        @Override public org.bukkit.util.Vector getVector(String p) { return parent.getVector(p); }
        @Override public org.bukkit.util.Vector getVector(String p, org.bukkit.util.Vector d) { return parent.getVector(p, d); }
        @Override public boolean isVector(String p) { return parent.isVector(p); }
        @Override public org.bukkit.OfflinePlayer getOfflinePlayer(String p) { return parent.getOfflinePlayer(p); }
        @Override public org.bukkit.OfflinePlayer getOfflinePlayer(String p, org.bukkit.OfflinePlayer d) { return parent.getOfflinePlayer(p, d); }
        @Override public boolean isOfflinePlayer(String p) { return parent.isOfflinePlayer(p); }
        @Override public org.bukkit.inventory.ItemStack getItemStack(String p) { return parent.getItemStack(p); }
        @Override public org.bukkit.inventory.ItemStack getItemStack(String p, org.bukkit.inventory.ItemStack d) { return parent.getItemStack(p, d); }
        @Override public boolean isItemStack(String p) { return parent.isItemStack(p); }
        @Override public org.bukkit.Color getColor(String p) { return parent.getColor(p); }
        @Override public org.bukkit.Color getColor(String p, org.bukkit.Color d) { return parent.getColor(p, d); }
        @Override public boolean isColor(String p) { return parent.isColor(p); }
        @Override public org.bukkit.Location getLocation(String p) { return parent.getLocation(p); }
        @Override public org.bukkit.Location getLocation(String p, org.bukkit.Location d) { return parent.getLocation(p, d); }
        @Override public boolean isLocation(String p) { return parent.isLocation(p); }
        @Override public boolean isConfigurationSection(String p) { return parent.isConfigurationSection(p); }
        @Override public org.bukkit.configuration.ConfigurationSection getDefaultSection() { return parent.getDefaultSection(); }
        @Override public void addDefault(String p, Object v) { parent.addDefault(p, v); }
        @Override public java.util.List<String> getComments(String p) { return parent.getComments(p); }
        @Override public java.util.List<String> getInlineComments(String p) { return parent.getInlineComments(p); }
        @Override public void setComments(String p, java.util.List<String> c) { parent.setComments(p, c); }
        @Override public void setInlineComments(String p, java.util.List<String> c) { parent.setInlineComments(p, c); }
    }
}
