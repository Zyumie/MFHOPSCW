package fr.zyumie.shopadmin.gui;

import fr.zyumie.shopadmin.ShopAdmin;
import fr.zyumie.shopadmin.ShopManager;
import fr.zyumie.shopadmin.model.ShopCategory;
import fr.zyumie.shopadmin.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShopGui {

    private final ShopAdmin plugin;
    private final ShopManager manager;

    private static final int[] ITEM_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    public ShopGui(ShopAdmin plugin, ShopManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    public void open(Player player) {
        List<ShopCategory> cats = manager.getCategoryList();
        open(player, cats.isEmpty() ? null : cats.get(0).getId(), 0);
    }

    public void open(Player player, String categoryId, int page) {
        FileConfiguration guiConfig = plugin.getGuiConfig();

        // Titre et taille depuis le Gui.yml
        String title = guiConfig.getString("main-gui.title", "&6&lBoutique Admin");
        int rows = guiConfig.getInt("main-gui.rows", 6);

        ShopHolder holder = new ShopHolder(ShopHolder.Type.MAIN, categoryId, page);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, mm(title));

        fillStructure(inv, guiConfig);
        buildCategoryBar(inv, categoryId);
        buildItems(inv, categoryId, page);
        buildNavigation(inv, categoryId, page, guiConfig);

        player.openInventory(inv);
    }

    private void fillStructure(Inventory inv, FileConfiguration guiConfig) {
        boolean fillEnabled = guiConfig.getBoolean("main-gui.fill.enabled", true);
        if (fillEnabled) {
            Material fillMat = parseMat(guiConfig.getString("main-gui.fill.material", "GRAY_STAINED_GLASS_PANE"));
            String fillName = guiConfig.getString("main-gui.fill.name", " ");
            ItemStack fill = makeItem(fillMat, fillName, null);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fill);
        }

        ItemStack border = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int row = 1; row <= 4; row++) {
            if (row * 9 + 8 < inv.getSize()) {
                inv.setItem(row * 9, border);
                inv.setItem(row * 9 + 8, border);
            }
        }
        for (int i = 45; i <= 53; i++) {
            if (i < inv.getSize() && (inv.getItem(i) == null || inv.getItem(i).getType() == parseMat(guiConfig.getString("main-gui.fill.material", "GRAY_STAINED_GLASS_PANE")))) {
                inv.setItem(i, border);
            }
        }
    }

    private void buildCategoryBar(Inventory inv, String activeCatId) {
        List<ShopCategory> cats = manager.getCategoryList();
        for (int i = 0; i < Math.min(cats.size(), 9); i++) {
            ShopCategory cat = cats.get(i);
            boolean active = cat.getId().equals(activeCatId);
            String suffix = active ? " &a✔" : "";
            List<String> lore = new ArrayList<>(cat.getIconLore());
            if (active) lore.add(0, "&a▶ Catégorie active");
            inv.setItem(i, makeItem(cat.getIcon(), cat.getIconName() + suffix, lore));
        }
    }

    private void buildItems(Inventory inv, String categoryId, int page) {
        if (categoryId == null) return;
        ShopCategory cat = manager.getCategories().get(categoryId);
        if (cat == null) return;

        List<ShopItem> items = cat.getItemsSorted();
        int start = page * ITEM_SLOTS.length;

        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= items.size()) break;
            if (ITEM_SLOTS[i] < inv.getSize()) {
                inv.setItem(ITEM_SLOTS[i], buildShopItemStack(items.get(idx)));
            }
        }
    }

    private void buildNavigation(Inventory inv, String categoryId, int page, FileConfiguration guiConfig) {
        ShopCategory cat  = categoryId != null ? manager.getCategories().get(categoryId) : null;
        int total = cat != null ? cat.getItemsSorted().size() : 0;
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) ITEM_SLOTS.length));

        if (page > 0) {
            int slot = guiConfig.getInt("main-gui.navigation.prev-page.slot", 45);
            Material mat = parseMat(guiConfig.getString("main-gui.navigation.prev-page.material", "ARROW"));
            String name = guiConfig.getString("main-gui.navigation.prev-page.name", "&7◀ Page précédente");
            List<String> lore = guiConfig.getStringList("main-gui.navigation.prev-page.lore");
            if(slot < inv.getSize()) inv.setItem(slot, makeItem(mat, name, lore));
        }

        if (page < totalPages - 1) {
            int slot = guiConfig.getInt("main-gui.navigation.next-page.slot", 53);
            Material mat = parseMat(guiConfig.getString("main-gui.navigation.next-page.material", "ARROW"));
            String name = guiConfig.getString("main-gui.navigation.next-page.name", "&7Page suivante ▶");
            List<String> lore = guiConfig.getStringList("main-gui.navigation.next-page.lore");
            if(slot < inv.getSize()) inv.setItem(slot, makeItem(mat, name, lore));
        }

        int indSlot = guiConfig.getInt("main-gui.page-indicator.slot", 48);
        Material indMat = parseMat(guiConfig.getString("main-gui.page-indicator.material", "BOOK"));
        String indName = guiConfig.getString("main-gui.page-indicator.name", "&7Page &e{page}&7/&e{max}")
                .replace("{page}", String.valueOf(page + 1)).replace("{max}", String.valueOf(totalPages));
        List<String> indLore = guiConfig.getStringList("main-gui.page-indicator.lore");
        if(indSlot < inv.getSize()) inv.setItem(indSlot, makeItem(indMat, indName, indLore));

        int closeSlot = guiConfig.getInt("main-gui.navigation.close.slot", 49);
        Material closeMat = parseMat(guiConfig.getString("main-gui.navigation.close.material", "BARRIER"));
        String closeName = guiConfig.getString("main-gui.navigation.close.name", "&cFermer");
        List<String> closeLore = guiConfig.getStringList("main-gui.navigation.close.lore");
        if(closeSlot < inv.getSize()) inv.setItem(closeSlot, makeItem(closeMat, closeName, closeLore));
    }

    private ItemStack buildShopItemStack(ShopItem si) {
        String buy  = si.canBuy()  ? String.format("%.2f", si.getBuyPrice())  : "&cDésactivé";
        String sell = si.canSell() ? String.format("%.2f", si.getSellPrice()) : "&cDésactivé";
        String stock = si.isUnlimitedStock() ? "∞" : String.valueOf(si.getStock());

        List<String> lore = new ArrayList<>();
        for (String line : si.getLore())
            lore.add(line.replace("{buy_price}", buy).replace("{sell_price}", sell).replace("{stock}", stock));
        lore.add("");
        lore.add("&7Stock: &e" + stock);
        lore.add("");
        lore.add("&eClique gauche &7→ Acheter");
        lore.add("&eClique droit &7→ Vendre");
        lore.add("&eShift+clic &7→ Choisir la quantité");

        ItemStack stack = makeItem(si.getMaterial(), si.getName(), lore);
        if (si.getCustomModelData() > 0) {
            ItemMeta m = stack.getItemMeta();
            m.setCustomModelData(si.getCustomModelData());
            stack.setItemMeta(m);
        }
        return stack;
    }

    public void openQuantitySelector(Player player, ShopItem item, boolean buying, ShopHolder parentHolder) {
        FileConfiguration guiConfig = plugin.getGuiConfig();
        ShopHolder holder = new ShopHolder(ShopHolder.Type.QUANTITY, parentHolder.getCategoryId(), parentHolder.getPage());
        holder.setPendingItem(item);
        holder.setBuying(buying);

        String title = guiConfig.getString("quantity-selector.title", "&8Sélectionner une quantité");
        int rows = guiConfig.getInt("quantity-selector.rows", 3);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, mm(title));

        boolean fillEnabled = guiConfig.getBoolean("quantity-selector.fill.enabled", true);
        if (fillEnabled) {
            Material fillMat = parseMat(guiConfig.getString("quantity-selector.fill.material", "BLACK_STAINED_GLASS_PANE"));
            String fillName = guiConfig.getString("quantity-selector.fill.name", " ");
            ItemStack fill = makeItem(fillMat, fillName, null);
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fill);
        }

        List<Integer> amounts = plugin.getConfig().getIntegerList(buying ? "shop.buy-amounts" : "shop.sell-amounts");
        if (amounts.isEmpty()) amounts = List.of(1, 8, 16, 32, 64);

        double unitPrice = buying ? item.getBuyPrice() : item.getSellPrice();
        int[] btnSlots = {10, 11, 12, 13, 14, 15, 16};

        String prefixPath = buying ? "quantity-selector.confirm-buy" : "quantity-selector.confirm-sell";
        Material btnMat = parseMat(guiConfig.getString(prefixPath + ".material", buying ? "LIME_STAINED_GLASS_PANE" : "ORANGE_STAINED_GLASS_PANE"));
        String btnNameTpl = guiConfig.getString(prefixPath + ".name", buying ? "&aAcheter x{amount}" : "&6Vendre x{amount}");
        List<String> btnLoreTpl = guiConfig.getStringList(prefixPath + ".lore");

        for (int i = 0; i < Math.min(amounts.size(), btnSlots.length); i++) {
            int amount  = amounts.get(i);
            double total = unitPrice * amount;
            String btnName = btnNameTpl.replace("{amount}", String.valueOf(amount));

            List<String> btnLore = new ArrayList<>();
            for (String line : btnLoreTpl) {
                btnLore.add(line.replace("{amount}", String.valueOf(amount)).replace("{total_price}", String.format("%.2f", total)));
            }
            if(btnSlots[i] < inv.getSize()) inv.setItem(btnSlots[i], makeItem(btnMat, btnName, btnLore));
        }

        int cancelSlot = guiConfig.getInt("quantity-selector.cancel.slot", 22);
        Material cancelMat = parseMat(guiConfig.getString("quantity-selector.cancel.material", "BARRIER"));
        String cancelName = guiConfig.getString("quantity-selector.cancel.name", "&cAnnuler");
        if(cancelSlot < inv.getSize()) inv.setItem(cancelSlot, makeItem(cancelMat, cancelName, null));

        player.openInventory(inv);
    }

    public static int[] getItemSlots() { return ITEM_SLOTS; }

    public ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(mm(name));
        if (lore != null && !lore.isEmpty())
            meta.lore(lore.stream().map(this::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private Material parseMat(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (Exception e) { return Material.STONE; }
    }

    private Component mm(String s) {
        return MiniMessage.miniMessage().deserialize(
                s.replace("&0","<black>").replace("&8","<dark_gray>")
                        .replace("&6","<gold>").replace("&e","<yellow>")
                        .replace("&a","<green>").replace("&c","<red>")
                        .replace("&b","<aqua>").replace("&7","<gray>")
                        .replace("&f","<white>").replace("&l","<bold>")
                        .replace("&r","<reset>").replace("&o","<italic>"));
    }
}