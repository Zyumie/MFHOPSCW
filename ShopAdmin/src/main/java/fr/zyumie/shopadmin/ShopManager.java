package fr.zyumie.shopadmin;

import fr.zyumie.shopadmin.model.ShopCategory;
import fr.zyumie.shopadmin.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager {
    public enum TransactionResult {
        SUCCESS, NO_ECONOMY, INSUFFICIENT_FUNDS, INSUFFICIENT_STOCK,
        INVENTORY_FULL, NOT_ENOUGH_ITEMS, DISABLED, DUPLICATE
    }

    private final ShopAdmin plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<String, String> customItemIds = new LinkedHashMap<>();
    private final Map<String, Long> recentPurchases = new LinkedHashMap<>();
    private Economy economy;

    public ShopManager(ShopAdmin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        loadEconomy();
        loadShop();
    }

    private void loadEconomy() {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            plugin.getLogger().info("Vault Economy détectée : " + economy.getName());
        } else {
            plugin.getLogger().severe("Vault Economy introuvable - les transactions sont désactivées.");
        }
    }

    public void loadShop() {
        categories.clear();
        customItemIds.clear();

        File shopFile = new File(plugin.getDataFolder(), "Shop.yml");
        if (!shopFile.exists()) {
            plugin.getLogger().warning("Shop.yml introuvable.");
            return;
        }

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
                        double buyPrice = is.getDouble("buy-price", -1.0);
                        double sellPrice = is.getDouble("sell-price", -1.0);
                        int stock = is.getInt("stock", -1);
                        int slot = is.getInt("slot", 0);
                        int cmd = is.getInt("custom-model-data", 0);

                        String trimPattern = is.getString("trim.pattern", null);
                        String trimMaterial = is.getString("trim.material", null);

                        String customItemId = is.getString("custom-item-id", "");
                        if (customItemId != null && !customItemId.isBlank()) {
                            customItemIds.put(key(catId, itemId), customItemId.trim());
                        }

                        category.addItem(new ShopItem(itemId, catId, mat, name, lore,
                                buyPrice, sellPrice, stock, slot, cmd, trimPattern, trimMaterial));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur chargement item " + itemId + " : " + e.getMessage());
                    }
                }
            }
            categories.put(catId, category);
        }

        plugin.getLogger().info(categories.size() + " catégories chargées.");
        if (!customItemIds.isEmpty()) {
            plugin.getLogger().info(customItemIds.size() + " entrées CustomItems liées au ShopAdmin.");
        }
    }

    public boolean isCustomItem(ShopItem item) {
        return customItemIds.containsKey(key(item.getCategoryId(), item.getId()));
    }

    public String getCustomItemId(ShopItem item) {
        return customItemIds.get(key(item.getCategoryId(), item.getId()));
    }

    public ItemStack getDisplayItem(ShopItem item) {
        String customId = getCustomItemId(item);
        if (customId != null) {
            ItemStack[] resolved = resolveCustomItems(customId);
            if (resolved != null && resolved.length > 0) {
                ItemStack chosen = null;

                if (customId.endsWith("_armor")) {
                    for (ItemStack stack : resolved) {
                        if (stack != null && stack.getType().name().endsWith("_CHESTPLATE")) {
                            chosen = stack;
                            break;
                        }
                    }
                }

                if (chosen == null) {
                    for (ItemStack stack : resolved) {
                        if (stack != null) {
                            chosen = stack;
                            break;
                        }
                    }
                }

                if (chosen != null) {
                    ItemStack copy = chosen.clone();
                    copy.setAmount(1);
                    return copy;
                }
            }
        }

        return buildVanillaItem(item, 1);
    }

    public TransactionResult buy(Player player, ShopItem item, int requestedAmount) {
        if (economy == null) return TransactionResult.NO_ECONOMY;
        if (!item.canBuy()) return TransactionResult.DISABLED;

        String customId = getCustomItemId(item);
        int amount = customId != null ? 1 : Math.max(1, requestedAmount);

        if (!item.hasStock(amount)) return TransactionResult.INSUFFICIENT_STOCK;

        if (customId != null) {
            long now = System.currentTimeMillis();
            // Purge les verrous expirés (largement au-delà de la fenêtre anti-doublon de 650ms)
            // pour que cette map ne grossisse pas indéfiniment sur un serveur qui tourne longtemps.
            recentPurchases.entrySet().removeIf(e -> now - e.getValue() > 5000L);

            String lockKey = player.getUniqueId() + ":" + key(item.getCategoryId(), item.getId());
            Long last = recentPurchases.get(lockKey);
            if (last != null && now - last < 650L) {
                return TransactionResult.DUPLICATE;
            }
            recentPurchases.put(lockKey, now);
        }

        double total = item.getBuyPrice() * amount;
        if (!economy.has(player, total)) return TransactionResult.INSUFFICIENT_FUNDS;

        if (customId != null) {
            ItemStack[] items = resolveCustomItems(customId);
            if (items == null || items.length == 0) {
                plugin.getLogger().warning("Impossible de générer l'item CustomItems '" + customId + "'. Achat annulé.");
                return TransactionResult.DISABLED;
            }

            List<ItemStack> toGive = new ArrayList<>();
            for (ItemStack stack : items) {
                if (stack != null) {
                    ItemStack copy = stack.clone();
                    copy.setAmount(1);
                    toGive.add(copy);
                }
            }

            if (countEmptyStorageSlots(player) < toGive.size()) {
                return TransactionResult.INVENTORY_FULL;
            }

            economy.withdrawPlayer(player, total);
            item.removeStock(1);
            player.getInventory().addItem(toGive.toArray(new ItemStack[0]));
            return TransactionResult.SUCCESS;
        }

        if (!hasRoomFor(player, item.getMaterial(), amount)) return TransactionResult.INVENTORY_FULL;
        economy.withdrawPlayer(player, total);
        item.removeStock(amount);
        ItemStack stack = buildVanillaItem(item, amount);
        player.getInventory().addItem(stack);
        return TransactionResult.SUCCESS;
    }

    /**
     * Vérifie que amount unités de mat rentrent réellement dans l'inventaire, en tenant
     * compte du max-stack-size du matériau (ex: les outils ont un max-stack de 1). L'ancien
     * check ne vérifiait qu'un seul slot vide, donc un achat en grande quantité d'un item à
     * stack réduit (ex: x8 pioches) était débité en entier alors que addItem() ne pouvait en
     * donner qu'une partie — le reste disparaissait silencieusement.
     */
    private boolean hasRoomFor(Player player, Material mat, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (contents == null) return false;

        int remaining = amount;
        for (ItemStack stack : contents) {
            if (stack != null && stack.getType() == mat) {
                remaining -= (stack.getMaxStackSize() - stack.getAmount());
            }
        }
        if (remaining <= 0) return true;

        int maxStackSize = mat.getMaxStackSize();
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) {
                remaining -= maxStackSize;
                if (remaining <= 0) return true;
            }
        }
        return remaining <= 0;
    }

    private ItemStack[] resolveCustomItems(String customId) {
        try {
            Plugin customItemsPlugin = plugin.getServer().getPluginManager().getPlugin("CustomItems");
            if (customItemsPlugin == null) {
                plugin.getLogger().warning("Plugin CustomItems introuvable.");
                return null;
            }

            Method getRegistry = customItemsPlugin.getClass().getMethod("getItemRegistry");
            Object registry = getRegistry.invoke(customItemsPlugin);
            if (registry == null) return null;

            Method resolve = registry.getClass().getMethod("resolveGiveItems", String.class);
            Object raw = resolve.invoke(registry, customId);
            if (raw instanceof ItemStack[] items) return items;
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur liaison CustomItems pour '" + customId + "' : "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    private int countEmptyStorageSlots(Player player) {
        int empty = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        if (contents == null) return 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() == Material.AIR) empty++;
        }
        return empty;
    }

    private String key(String categoryId, String itemId) {
        return categoryId + ":" + itemId;
    }

    public TransactionResult sell(Player player, ShopItem item, int amount) {
        if (economy == null) return TransactionResult.NO_ECONOMY;
        if (!item.canSell()) return TransactionResult.DISABLED;

        boolean allowEnchanted = plugin.getConfig().getBoolean("shop.allow-enchanted-sell", false);
        boolean allowDamaged = plugin.getConfig().getBoolean("shop.allow-damaged-sell", false);

        int has = countInInventory(player, item.getMaterial(), allowEnchanted, allowDamaged);
        if (has < amount) return TransactionResult.NOT_ENOUGH_ITEMS;

        removeFromInventory(player, item.getMaterial(), amount, allowEnchanted, allowDamaged);
        item.addStock(amount);
        economy.depositPlayer(player, item.getSellPrice() * amount);
        return TransactionResult.SUCCESS;
    }

    public Map<String, ShopCategory> getCategories() { return categories; }
    public Economy getEconomy() { return economy; }
    public List<ShopCategory> getCategoryList() { return new ArrayList<>(categories.values()); }

    private ItemStack buildVanillaItem(ShopItem item, int amount) {
        ItemStack stack = new ItemStack(item.getMaterial(), amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(color(item.getName()));
            if (item.getCustomModelData() > 0) {
                meta.setCustomModelData(item.getCustomModelData());
            }

            if (meta instanceof ArmorMeta armorMeta
                    && item.getTrimPattern() != null && item.getTrimMaterial() != null) {
                try {
                    TrimPattern pattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(item.getTrimPattern().toLowerCase()));
                    TrimMaterial trimMat = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(item.getTrimMaterial().toLowerCase()));

                    if (pattern != null && trimMat != null) {
                        armorMeta.setTrim(new ArmorTrim(trimMat, pattern));
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur trim sur l'item " + item.getId() + " : " + e.getMessage());
                }
            }

            stack.setItemMeta(meta);
        }
        return stack;
    }

    private int countInInventory(Player player, Material mat, boolean allowEnchanted, boolean allowDamaged) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() != mat) continue;
            if (!isSellable(stack, allowEnchanted, allowDamaged)) continue;
            count += stack.getAmount();
        }
        return count;
    }

    private void removeFromInventory(Player player, Material mat, int amount, boolean allowEnchanted, boolean allowDamaged) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            if (!isSellable(stack, allowEnchanted, allowDamaged)) continue;
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
    }

    /**
     * Applique shop.allow-enchanted-sell et shop.allow-damaged-sell : ces deux clés existaient
     * dans config.yml mais n'étaient lues nulle part, donc un item enchanté ou quasi-cassé se
     * vendait au même prix qu'un neuf. Les copies non éligibles sont simplement ignorées (ni
     * comptées, ni retirées) — si le joueur n'en a pas assez d'éligibles, il retombe sur le
     * message "pas assez de {item}" déjà existant.
     */
    private boolean isSellable(ItemStack stack, boolean allowEnchanted, boolean allowDamaged) {
        if (!allowEnchanted && !stack.getEnchantments().isEmpty()) return false;
        if (!allowDamaged) {
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof Damageable dmg && dmg.getDamage() > 0) return false;
        }
        return true;
    }

    private Material parseMat(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.STONE; }
    }

    private Component color(String s) {
        if (s == null) s = "";
        return MiniMessage.miniMessage().deserialize(s);
    }
}