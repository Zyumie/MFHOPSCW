package fr.zyumie.shopadmin;

import fr.zyumie.shopadmin.model.ShopCategory;
import fr.zyumie.shopadmin.model.ShopItem;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager {

    private final ShopAdmin plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private Economy economy;

    public ShopManager(ShopAdmin plugin) {
        this.plugin = plugin;
    }

    // ── Chargement ───────────────────────────────────────────────────────

    public void loadAll() {
        loadEconomy();
        loadShop();
    }

    private void loadEconomy() {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            plugin.getLogger().info("Economy: " + economy.getName());
        } else {
            plugin.getLogger().severe("Vault Economy introuvable — les transactions sont désactivées.");
        }
    }

    public void loadShop() {
        categories.clear();
        File shopFile = new File(plugin.getDataFolder(), "Shop.yml");
        if (!shopFile.exists()) { plugin.getLogger().warning("Shop.yml introuvable."); return; }

        YamlConfiguration shopCfg = YamlConfiguration.loadConfiguration(shopFile);
        ConfigurationSection cats = shopCfg.getConfigurationSection("categories");
        if (cats == null) return;

        for (String catId : cats.getKeys(false)) {
            ConfigurationSection catSec = cats.getConfigurationSection(catId);
            if (catSec == null) continue;

            Material icon = parseMat(catSec.getString("icon", "CHEST"));
            String catName = catSec.getString("name", catId);
            String iconName = catSec.getString("icon-name", catName);
            List<String> iconLore = catSec.getStringList("icon-lore");

            ShopCategory category = new ShopCategory(catId, catName, icon, iconName, iconLore);

            ConfigurationSection itemsSec = catSec.getConfigurationSection("items");
            if (itemsSec != null) {
                for (String itemId : itemsSec.getKeys(false)) {
                    ConfigurationSection is = itemsSec.getConfigurationSection(itemId);
                    if (is == null) continue;
                    try {
                        Material mat = parseMat(is.getString("material", "STONE"));
                        String name = is.getString("name", itemId);
                        List<String> lore = is.getStringList("lore");
                        double buyPrice  = is.getDouble("buy-price", -1);
                        double sellPrice = is.getDouble("sell-price", -1);
                        int stock = is.getInt("stock", -1);
                        int slot  = is.getInt("slot", 0);
                        int cmd   = is.getInt("custom-model-data", 0);
                        category.addItem(new ShopItem(itemId, catId, mat, name, lore,
                                buyPrice, sellPrice, stock, slot, cmd));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Item invalide '" + itemId + "': " + e.getMessage());
                    }
                }
            }
            categories.put(catId, category);
        }
        plugin.getLogger().info("Shop chargé: " + categories.size() + " catégorie(s).");
    }

    // ── Transactions ─────────────────────────────────────────────────────

    public enum TransactionResult { SUCCESS, NO_ECONOMY, INSUFFICIENT_FUNDS,
        INSUFFICIENT_STOCK, INVENTORY_FULL, NOT_ENOUGH_ITEMS, DISABLED }

    public TransactionResult buy(Player player, ShopItem item, int amount) {
        if (economy == null) return TransactionResult.NO_ECONOMY;
        if (!item.canBuy()) return TransactionResult.DISABLED;
        if (!item.hasStock(amount)) return TransactionResult.INSUFFICIENT_STOCK;

        double total = item.getBuyPrice() * amount;
        if (!economy.has(player, total)) return TransactionResult.INSUFFICIENT_FUNDS;
        if (player.getInventory().firstEmpty() == -1) return TransactionResult.INVENTORY_FULL;

        economy.withdrawPlayer(player, total);
        item.removeStock(amount);

        var stack = buildItem(item, amount);
        player.getInventory().addItem(stack);
        return TransactionResult.SUCCESS;
    }

    public TransactionResult sell(Player player, ShopItem item, int amount) {
        if (economy == null) return TransactionResult.NO_ECONOMY;
        if (!item.canSell()) return TransactionResult.DISABLED;

        int has = countInInventory(player, item.getMaterial());
        if (has < amount) return TransactionResult.NOT_ENOUGH_ITEMS;

        removeFromInventory(player, item.getMaterial(), amount);
        item.addStock(amount);
        economy.depositPlayer(player, item.getSellPrice() * amount);
        return TransactionResult.SUCCESS;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    public Map<String, ShopCategory> getCategories() { return categories; }
    public Economy getEconomy() { return economy; }

    public List<ShopCategory> getCategoryList() { return new ArrayList<>(categories.values()); }

    private org.bukkit.inventory.ItemStack buildItem(ShopItem item, int amount) {
        var stack = new org.bukkit.inventory.ItemStack(item.getMaterial(), amount);
        var meta = stack.getItemMeta();
        meta.displayName(color(item.getName()));
        if (item.getCustomModelData() > 0) meta.setCustomModelData(item.getCustomModelData());
        stack.setItemMeta(meta);
        return stack;
    }

    private int countInInventory(Player player, Material mat) {
        int count = 0;
        for (var stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) count += stack.getAmount();
        }
        return count;
    }

    private void removeFromInventory(Player player, Material mat, int amount) {
        int remaining = amount;
        var contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            var stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
    }

    private Material parseMat(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.STONE; }
    }

    private net.kyori.adventure.text.Component color(String s) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                s.replace("&8","<dark_gray>").replace("&6","<gold>").replace("&e","<yellow>")
                 .replace("&a","<green>").replace("&c","<red>").replace("&b","<aqua>")
                 .replace("&7","<gray>").replace("&f","<white>").replace("&r","<reset>")
                 .replace("&l","<bold>").replace("&o","<italic>"));
    }
}
