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

    public ShopGui(ShopAdmin plugin, ShopManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    public void openHub(Player player) {
        FileConfiguration guiConfig = plugin.getGuiConfig();

        String title = guiConfig.getString("hub.title", "<gold><bold>Boutique - Menu Principal");
        int rows = guiConfig.getInt("hub.rows", 3);

        ShopHolder holder = new ShopHolder(ShopHolder.Type.HUB, null, 0);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, mm(title));

        int[] catSlots = getHubCategorySlots(guiConfig);

        fillStructure(inv, guiConfig, catSlots, "hub");
        buildHubCategories(inv, catSlots);
        buildHubNavigation(inv, guiConfig);

        player.openInventory(inv);
    }

    public void open(Player player, String categoryId, int page) {
        FileConfiguration guiConfig = plugin.getGuiConfig();

        String title = guiConfig.getString("main-gui.title", "<gold><bold>Boutique Admin");
        int rows = guiConfig.getInt("main-gui.rows", 6);

        ShopHolder holder = new ShopHolder(ShopHolder.Type.MAIN, categoryId, page);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, mm(title));

        int[] itemSlots = computeItemSlots(guiConfig, rows);

        fillStructure(inv, guiConfig, itemSlots, "main-gui");
        buildItems(inv, categoryId, page, itemSlots);
        buildNavigation(inv, categoryId, page, guiConfig, itemSlots.length);

        player.openInventory(inv);
    }

    /**
     * Calcule un quadrillage de slots (colonnes 1 à 7 de chaque rangée couverte, les colonnes
     * 0 et 8 restant pour la bordure) à partir d'un start-slot/end-slot donné. Utilisé à la
     * fois pour la zone d'items (main-gui.items-area) et pour la grille de catégories du hub
     * (hub.categories-area), pour garder exactement la même logique aux deux endroits.
     */
    private static int[] computeGridSlots(FileConfiguration guiConfig, int rows, String startPath, String endPath, int defaultStart, int defaultEnd) {
        int start = guiConfig.getInt(startPath, defaultStart);
        int end = guiConfig.getInt(endPath, defaultEnd);

        int startRow = Math.max(0, start / 9);
        int endRow = Math.min(rows - 1, end / 9);
        if (endRow < startRow) return new int[0];

        List<Integer> slots = new ArrayList<>();
        for (int row = startRow; row <= endRow; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }

        int[] result = new int[slots.size()];
        for (int i = 0; i < result.length; i++) result[i] = slots.get(i);
        return result;
    }

    private static int[] computeItemSlots(FileConfiguration guiConfig, int rows) {
        return computeGridSlots(guiConfig, rows, "main-gui.items-area.start-slot", "main-gui.items-area.end-slot", 9, 44);
    }

    private static int[] computeHubCategorySlots(FileConfiguration guiConfig, int rows) {
        return computeGridSlots(guiConfig, rows, "hub.categories-area.start-slot", "hub.categories-area.end-slot", 9, 17);
    }

    public static int[] getHubCategorySlots(FileConfiguration guiConfig) {
        int rows = guiConfig.getInt("hub.rows", 3);
        return computeHubCategorySlots(guiConfig, rows);
    }

    private void fillStructure(Inventory inv, FileConfiguration guiConfig, int[] contentSlots, String prefix) {
        // Remplissage de fond (activable/désactivable dans le YAML)
        if (guiConfig.getBoolean(prefix + ".fill.enabled", false)) {
            Material fillMat = parseMat(guiConfig.getString(prefix + ".fill.material", "AIR"));
            if (fillMat != Material.AIR) {
                ItemStack fill = makeItem(fillMat, guiConfig.getString(prefix + ".fill.name", " "), null);
                for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fill);
            }
        }

        // Bordures (activables/désactivables dans le YAML)
        if (guiConfig.getBoolean(prefix + ".fill.border.enabled", false)) {
            Material borderMat = parseMat(guiConfig.getString(prefix + ".fill.border.material", "AIR"));
            if (borderMat != Material.AIR) {
                ItemStack border = makeItem(borderMat, " ", null);

                // Encadre les colonnes 0 et 8 de chaque rangée réellement occupée par le
                // contenu (items ou catégories), pour rester cohérent si la zone change.
                if (contentSlots.length > 0) {
                    int minRow = contentSlots[0] / 9, maxRow = contentSlots[0] / 9;
                    for (int slot : contentSlots) {
                        int row = slot / 9;
                        if (row < minRow) minRow = row;
                        if (row > maxRow) maxRow = row;
                    }
                    for (int row = minRow; row <= maxRow; row++) {
                        if (row * 9 + 8 < inv.getSize()) {
                            inv.setItem(row * 9, border);
                            inv.setItem(row * 9 + 8, border);
                        }
                    }
                }

                // Dernière rangée : comble ce qui n'a pas déjà été posé par le contenu.
                // Basé sur inv.getSize() plutôt que 45..53 en dur, pour s'adapter à n'importe
                // quel nombre de rangées (main-gui.rows / hub.rows), pas seulement 6.
                for (int i = Math.max(0, inv.getSize() - 9); i < inv.getSize(); i++) {
                    if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                        inv.setItem(i, border);
                    }
                }
            }
        }
    }

    private void buildHubCategories(Inventory inv, int[] slots) {
        List<ShopCategory> cats = manager.getCategoryList();
        for (int i = 0; i < Math.min(cats.size(), slots.length); i++) {
            ShopCategory cat = cats.get(i);
            inv.setItem(slots[i], makeItem(cat.getIcon(), cat.getIconName(), new ArrayList<>(cat.getIconLore())));
        }
    }

    private void buildHubNavigation(Inventory inv, FileConfiguration guiConfig) {
        if (guiConfig.contains("hub.navigation.close")) {
            int closeSlot = guiConfig.getInt("hub.navigation.close.slot", 22);
            Material closeMat = parseMat(guiConfig.getString("hub.navigation.close.material", "AIR"));
            if (closeMat != Material.AIR && closeSlot < inv.getSize()) {
                inv.setItem(closeSlot, makeItem(closeMat, guiConfig.getString("hub.navigation.close.name", "<red>Fermer"), guiConfig.getStringList("hub.navigation.close.lore")));
            }
        }
    }

    private void buildItems(Inventory inv, String categoryId, int page, int[] itemSlots) {
        if (categoryId == null) return;
        ShopCategory cat = manager.getCategories().get(categoryId);
        if (cat == null) return;

        List<ShopItem> items = cat.getItemsSorted();
        int start = page * itemSlots.length;

        for (int i = 0; i < itemSlots.length; i++) {
            int idx = start + i;
            if (idx >= items.size()) break;
            if (itemSlots[i] < inv.getSize()) {
                inv.setItem(itemSlots[i], buildShopItemStack(items.get(idx)));
            }
        }
    }

    private void buildNavigation(Inventory inv, String categoryId, int page, FileConfiguration guiConfig, int itemsPerPage) {
        ShopCategory cat  = categoryId != null ? manager.getCategories().get(categoryId) : null;
        int total = cat != null ? cat.getItemsSorted().size() : 0;
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) Math.max(1, itemsPerPage)));

        if (guiConfig.contains("main-gui.navigation.back")) {
            int backSlot = guiConfig.getInt("main-gui.navigation.back.slot", 46);
            Material backMat = parseMat(guiConfig.getString("main-gui.navigation.back.material", "AIR"));
            if (backMat != Material.AIR && backSlot < inv.getSize()) {
                inv.setItem(backSlot, makeItem(backMat, guiConfig.getString("main-gui.navigation.back.name", "<yellow>« Retour"), guiConfig.getStringList("main-gui.navigation.back.lore")));
            }
        }

        if (page > 0 && guiConfig.contains("main-gui.navigation.prev-page")) {
            int slot = guiConfig.getInt("main-gui.navigation.prev-page.slot", 45);
            Material mat = parseMat(guiConfig.getString("main-gui.navigation.prev-page.material", "AIR"));
            if (mat != Material.AIR && slot < inv.getSize()) {
                inv.setItem(slot, makeItem(mat, guiConfig.getString("main-gui.navigation.prev-page.name", ""), guiConfig.getStringList("main-gui.navigation.prev-page.lore")));
            }
        }

        if (page < totalPages - 1 && guiConfig.contains("main-gui.navigation.next-page")) {
            int slot = guiConfig.getInt("main-gui.navigation.next-page.slot", 53);
            Material mat = parseMat(guiConfig.getString("main-gui.navigation.next-page.material", "AIR"));
            if (mat != Material.AIR && slot < inv.getSize()) {
                inv.setItem(slot, makeItem(mat, guiConfig.getString("main-gui.navigation.next-page.name", ""), guiConfig.getStringList("main-gui.navigation.next-page.lore")));
            }
        }

        if (guiConfig.contains("main-gui.page-indicator")) {
            int indSlot = guiConfig.getInt("main-gui.page-indicator.slot", 48);
            Material indMat = parseMat(guiConfig.getString("main-gui.page-indicator.material", "AIR"));
            if (indMat != Material.AIR && indSlot < inv.getSize()) {
                String indName = guiConfig.getString("main-gui.page-indicator.name", "<gray>Page <yellow>{page}<gray>/<yellow>{max}")
                        .replace("{page}", String.valueOf(page + 1)).replace("{max}", String.valueOf(totalPages));
                inv.setItem(indSlot, makeItem(indMat, indName, guiConfig.getStringList("main-gui.page-indicator.lore")));
            }
        }

        if (guiConfig.contains("main-gui.navigation.close")) {
            int closeSlot = guiConfig.getInt("main-gui.navigation.close.slot", 49);
            Material closeMat = parseMat(guiConfig.getString("main-gui.navigation.close.material", "AIR"));
            if (closeMat != Material.AIR && closeSlot < inv.getSize()) {
                inv.setItem(closeSlot, makeItem(closeMat, guiConfig.getString("main-gui.navigation.close.name", ""), guiConfig.getStringList("main-gui.navigation.close.lore")));
            }
        }
    }

    private ItemStack buildShopItemStack(ShopItem si) {
        String buy  = si.canBuy()  ? String.format("%.2f", si.getBuyPrice())  : "<red>Désactivé";
        String sell = si.canSell() ? String.format("%.2f", si.getSellPrice()) : "<red>Désactivé";
        String stock = si.isUnlimitedStock() ? "∞" : String.valueOf(si.getStock());

        List<String> loreText = new ArrayList<>();
        for (String line : si.getLore()) {
            loreText.add(line
                    .replace("{buy_price}", buy)
                    .replace("{sell_price}", sell)
                    .replace("{stock}", stock));
        }
        loreText.add("");
        loreText.add("<gray>Stock: <yellow>" + stock);
        loreText.add("");

        boolean isCustom = manager.isCustomItem(si);
        if (!isCustom) {
            loreText.add("<yellow>Clique gauche <gray>→ Acheter");
            loreText.add("<yellow>Clique droit <gray>→ Vendre");
            loreText.add("<yellow>Shift+clic <gray>→ Choisir la quantité");
        } else {
            loreText.add("<yellow>Clique gauche <gray>→ Acheter");
            loreText.add("<dark_gray>Achat unique : x1");
        }

        ItemStack stack = manager.getDisplayItem(si);
        if (stack == null) stack = new ItemStack(si.getMaterial());
        stack.setAmount(1);

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(mm(si.getName()));
            List<Component> loreComponents = new ArrayList<>();
            for (String line : loreText) loreComponents.add(mm(line));
            meta.lore(loreComponents);

            if (si.getCustomModelData() > 0) {
                meta.setCustomModelData(si.getCustomModelData());
            }
            stack.setItemMeta(meta);
        }

        return stack;
    }

    public void openQuantitySelector(Player player, ShopItem item, boolean buying, ShopHolder parentHolder) {
        FileConfiguration guiConfig = plugin.getGuiConfig();
        ShopHolder holder = new ShopHolder(ShopHolder.Type.QUANTITY, parentHolder.getCategoryId(), parentHolder.getPage());
        holder.setPendingItem(item);
        holder.setBuying(buying);

        String title = guiConfig.getString("quantity-selector.title", "<dark_gray>Sélectionner une quantité");
        int rows = guiConfig.getInt("quantity-selector.rows", 3);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, mm(title));

        if (guiConfig.getBoolean("quantity-selector.fill.enabled", false)) {
            Material fillMat = parseMat(guiConfig.getString("quantity-selector.fill.material", "AIR"));
            if (fillMat != Material.AIR) {
                ItemStack fill = makeItem(fillMat, guiConfig.getString("quantity-selector.fill.name", " "), null);
                for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fill);
            }
        }

        List<Integer> amounts;
        if (manager.isCustomItem(item)) {
            amounts = List.of(1);
        } else {
            amounts = plugin.getConfig().getIntegerList(buying ? "shop.buy-amounts" : "shop.sell-amounts");
            if (amounts == null || amounts.isEmpty()) amounts = List.of(1, 8, 16, 32, 64);
        }

        double unitPrice = buying ? item.getBuyPrice() : item.getSellPrice();
        int[] btnSlots = {10, 11, 12, 13, 14, 15, 16};

        String prefixPath = buying ? "quantity-selector.confirm-buy" : "quantity-selector.confirm-sell";
        Material btnMat = parseMat(guiConfig.getString(prefixPath + ".material", buying ? "LIME_STAINED_GLASS_PANE" : "ORANGE_STAINED_GLASS_PANE"));
        String btnNameTpl = guiConfig.getString(prefixPath + ".name", buying ? "<green>Acheter x{amount}" : "<gold>Vendre x{amount}");
        List<String> btnLoreTpl = guiConfig.getStringList(prefixPath + ".lore");

        for (int i = 0; i < Math.min(amounts.size(), btnSlots.length); i++) {
            int amount  = amounts.get(i);
            double total = unitPrice * amount;
            String btnName = btnNameTpl.replace("{amount}", String.valueOf(amount));

            List<String> btnLore = new ArrayList<>();
            for (String line : btnLoreTpl) {
                btnLore.add(line.replace("{amount}", String.valueOf(amount)).replace("{total_price}", String.format("%.2f", total)));
            }
            if (btnSlots[i] < inv.getSize()) inv.setItem(btnSlots[i], makeItem(btnMat, btnName, btnLore));
        }

        if (guiConfig.contains("quantity-selector.cancel")) {
            int cancelSlot = guiConfig.getInt("quantity-selector.cancel.slot", 22);
            Material cancelMat = parseMat(guiConfig.getString("quantity-selector.cancel.material", "AIR"));
            if (cancelMat != Material.AIR && cancelSlot < inv.getSize()) {
                inv.setItem(cancelSlot, makeItem(cancelMat, guiConfig.getString("quantity-selector.cancel.name", "<red>Annuler"), null));
            }
        }

        player.openInventory(inv);
    }

    public static int[] getItemSlots(FileConfiguration guiConfig) {
        int rows = guiConfig.getInt("main-gui.rows", 6);
        return computeItemSlots(guiConfig, rows);
    }

    public ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat != null ? mat : Material.STONE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(mm(name == null ? "" : name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> components = new ArrayList<>();
                for (String line : lore) components.add(mm(line));
                meta.lore(components);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material parseMat(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (Exception e) { return Material.AIR; } // On retourne AIR si c'est invalide
    }

    private Component mm(String s) {
        if (s == null) s = "";
        return MiniMessage.miniMessage().deserialize(s);
    }
}