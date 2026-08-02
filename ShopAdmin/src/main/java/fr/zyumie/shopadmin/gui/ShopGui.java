package fr.zyumie.shopadmin.gui;

import fr.zyumie.shopadmin.ShopAdmin;
import fr.zyumie.shopadmin.ShopManager;
import fr.zyumie.shopadmin.model.ShopCategory;
import fr.zyumie.shopadmin.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

    // Layout fixe (6 rangées = 54 slots)
    // Rangée 0  (0-8)  : catégories + fill
    // Rangées 1-4 (9-44): bordure noire aux colonnes 0 et 8, items au centre (7 colonnes)
    // Rangée 5 (45-53) : navigation
    private static final int[] ITEM_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    }; // 28 items par page

    private static final Material FILL_MAT   = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material BORDER_MAT = Material.BLACK_STAINED_GLASS_PANE;

    public ShopGui(ShopAdmin plugin, ShopManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // ── Ouverture principale ──────────────────────────────────────────────

    public void open(Player player) {
        List<ShopCategory> cats = manager.getCategoryList();
        open(player, cats.isEmpty() ? null : cats.get(0).getId(), 0);
    }

    public void open(Player player, String categoryId, int page) {
        ShopHolder holder = new ShopHolder(ShopHolder.Type.MAIN, categoryId, page);
        Inventory inv = Bukkit.createInventory(holder, 54, mm("&6&lBoutique Admin"));

        fillStructure(inv);
        buildCategoryBar(inv, categoryId);
        buildItems(inv, categoryId, page);
        buildNavigation(inv, categoryId, page);

        player.openInventory(inv);
    }

    // ── Structure ─────────────────────────────────────────────────────────

    private void fillStructure(Inventory inv) {
        ItemStack fill   = makeItem(FILL_MAT,   " ", null);
        ItemStack border = makeItem(BORDER_MAT, " ", null);

        // Fond général
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        // Bordure noire (colonnes 0 et 8 des rangées 1-4)
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 8, border);
        }
        // Rangée 5 : barre de navigation full bordure
        for (int i = 45; i <= 53; i++) inv.setItem(i, border);
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
            inv.setItem(ITEM_SLOTS[i], buildShopItemStack(items.get(idx)));
        }
    }

    private void buildNavigation(Inventory inv, String categoryId, int page) {
        ShopCategory cat  = categoryId != null ? manager.getCategories().get(categoryId) : null;
        int total = cat != null ? cat.getItemsSorted().size() : 0;
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) ITEM_SLOTS.length));

        if (page > 0)
            inv.setItem(45, makeItem(Material.ARROW, "&7◀ Page précédente", null));
        if (page < totalPages - 1)
            inv.setItem(53, makeItem(Material.ARROW, "&7Page suivante ▶", null));

        inv.setItem(49, makeItem(Material.MAP,
                "&7Page &e" + (page + 1) + "&7/&e" + totalPages, null));
        inv.setItem(47, makeItem(Material.BARRIER, "&cFermer", null));
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

    // ── Sélecteur de quantité ─────────────────────────────────────────────

    public void openQuantitySelector(Player player, ShopItem item, boolean buying,
                                     ShopHolder parentHolder) {
        ShopHolder holder = new ShopHolder(ShopHolder.Type.QUANTITY,
                parentHolder.getCategoryId(), parentHolder.getPage());
        holder.setPendingItem(item);
        holder.setBuying(buying);

        Inventory inv = Bukkit.createInventory(holder, 27,
                mm(buying ? "&6&lAcheter — " + item.getName() : "&6&lVendre — " + item.getName()));

        ItemStack fill = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);

        List<Integer> amounts = plugin.getConfig().getIntegerList(
                buying ? "shop.buy-amounts" : "shop.sell-amounts");
        if (amounts.isEmpty()) amounts = List.of(1, 8, 16, 32, 64);

        double unitPrice = buying ? item.getBuyPrice() : item.getSellPrice();
        // Centre les boutons sur la rangée du milieu (slots 9-17)
        int[] btnSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(amounts.size(), btnSlots.length); i++) {
            int amount  = amounts.get(i);
            double total = unitPrice * amount;
            Material mat = buying ? Material.LIME_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
            String name  = (buying ? "&ax" : "&6x") + amount;
            List<String> lore = List.of(
                    "&7Total: &e" + String.format("%.2f", total),
                    "", "&aCliquer pour confirmer");
            inv.setItem(btnSlots[i], makeItem(mat, name, lore));
        }
        inv.setItem(22, makeItem(Material.BARRIER, "&cAnnuler", null));

        player.openInventory(inv);
    }

    // ── Utils ─────────────────────────────────────────────────────────────

    public static int[] getItemSlots() { return ITEM_SLOTS; }

    public ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(mm(name));
        if (lore != null && !lore.isEmpty())
            meta.lore(lore.stream().map(this::mm).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                          ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
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
