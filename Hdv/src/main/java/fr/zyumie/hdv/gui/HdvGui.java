package fr.zyumie.hdv.gui;

import fr.zyumie.hdv.HdvManager;
import fr.zyumie.hdv.model.HdvListing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class HdvGui {

    private final JavaPlugin plugin;
    private final HdvManager manager;

    // Slots d'annonces (rangées 1-4, colonnes 1-7 = 28 annonces/page)
    private static final int[] LISTING_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    };

    private static final Material FILL   = Material.PURPLE_STAINED_GLASS_PANE;
    private static final Material BORDER = Material.BLACK_STAINED_GLASS_PANE;

    public HdvGui(JavaPlugin plugin, HdvManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    // ── Main GUI ──────────────────────────────────────────────────────────

    public HdvHolder openMain(Player player, boolean myListings, int page) {
        HdvHolder holder = new HdvHolder(HdvHolder.Type.MAIN);
        holder.setMyListings(myListings);
        holder.setPage(page);

        Inventory inv = Bukkit.createInventory(holder, 54, mm("&5&l✦ Hôtel des Ventes"));
        fillStructure(inv);
        buildFilterBar(inv);

        List<HdvListing> listings = myListings
                ? manager.getPlayerListings(player.getUniqueId())
                : manager.getActiveListings();

        int totalPages = Math.max(1, (int) Math.ceil(listings.size() / (double) LISTING_SLOTS.length));
        page = Math.max(0, Math.min(page, totalPages - 1));
        holder.setPage(page);

        int start = page * LISTING_SLOTS.length;
        for (int i = 0; i < LISTING_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= listings.size()) break;
            inv.setItem(LISTING_SLOTS[i], buildListingItem(listings.get(idx), player));
        }

        buildMainNav(inv, page, totalPages);
        player.openInventory(inv);
        return holder;
    }

    private void fillStructure(Inventory inv) {
        ItemStack fill   = item(FILL,   " ", null, 0);
        ItemStack border = item(BORDER, " ", null, 0);
        for (int i = 0; i < 54; i++) inv.setItem(i, fill);
        for (int row = 1; row <= 4; row++) {
            inv.setItem(row * 9,     border);
            inv.setItem(row * 9 + 8, border);
        }
        for (int i = 45; i <= 53; i++) inv.setItem(i, border);
    }

    private void buildFilterBar(Inventory inv) {
        inv.setItem(0, item(Material.NETHER_STAR,  "&f✦ Toutes les annonces",
                List.of("&7Voir toutes les annonces"), 0));
        inv.setItem(1, item(Material.CHEST, "&e⬡ Mes annonces",
                List.of("&7Gérer tes annonces actives"), 0));
        inv.setItem(2, item(Material.GOLD_INGOT, "&6✚ Mettre en vente",
                List.of("&7Tiens l'item en main et clique"), 0));
        inv.setItem(3, item(Material.ENDER_CHEST, "&a⬇ Récupérer",
                List.of("&7Récupérer items expirés/annulés"), 0));
    }

    private void buildMainNav(Inventory inv, int page, int totalPages) {
        if (page > 0)
            inv.setItem(45, item(Material.ARROW, "&7◀ Page précédente", null, 0));
        if (page < totalPages - 1)
            inv.setItem(53, item(Material.ARROW, "&7Page suivante ▶", null, 0));
        inv.setItem(49, item(Material.MAP,
                "&7Page &e" + (page + 1) + "&7/&e" + totalPages, null, 0));
        inv.setItem(47, item(Material.BARRIER, "&c✖ Fermer", null, 0));
    }

    private ItemStack buildListingItem(HdvListing listing, Player viewer) {
        ItemStack base = listing.getItem().clone();
        ItemMeta meta = base.getItemMeta();
        String currency = manager.getEconomy() != null ? manager.getEconomy().currencyNamePlural() : "";

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.lore());
        lore.add(mm(""));
        lore.add(mm("&7Vendeur : &e" + listing.getSellerName()));
        lore.add(mm("&7Prix    : &e" + String.format("%.2f", listing.getPrice()) + " " + currency));
        lore.add(mm("&7Expire  : &e" + listing.getTimeRemaining()));
        lore.add(mm(""));
        if (listing.getSellerUuid().equals(viewer.getUniqueId())) {
            lore.add(mm("&cClique droit &7pour annuler"));
        } else {
            lore.add(mm("&aClique gauche &7pour acheter"));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        base.setItemMeta(meta);
        return base;
    }

    // ── Confirm GUI ───────────────────────────────────────────────────────

    public HdvHolder openConfirm(Player player, HdvListing listing, HdvHolder parent) {
        HdvHolder holder = new HdvHolder(HdvHolder.Type.CONFIRM_BUY);
        holder.setPendingListing(listing);
        holder.setMyListings(parent.isMyListings());
        holder.setPage(parent.getPage());

        Inventory inv = Bukkit.createInventory(holder, 27, mm("&5&lConfirmer l'achat"));
        ItemStack fill = item(Material.BLACK_STAINED_GLASS_PANE, " ", null, 0);
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);

        String currency = manager.getEconomy() != null ? manager.getEconomy().currencyNamePlural() : "";
        String price    = String.format("%.2f", listing.getPrice()) + " " + currency;

        inv.setItem(11, item(Material.RED_STAINED_GLASS_PANE, "&c✖ Annuler",
                List.of("&7Retourner à la liste"), 0));
        inv.setItem(13, listing.getItem().clone());
        inv.setItem(15, item(Material.LIME_STAINED_GLASS_PANE, "&a✔ Confirmer",
                List.of("&7Item : &f" + getDisplayName(listing.getItem()),
                        "&7Prix : &e" + price, "", "&aCliquer pour confirmer"), 0));

        player.openInventory(inv);
        return holder;
    }

    // ── Create GUI ────────────────────────────────────────────────────────

    public HdvHolder openCreate(Player player, ItemStack item, double price, int duration) {
        HdvHolder holder = new HdvHolder(HdvHolder.Type.CREATE);
        holder.setPendingItem(item.clone());
        holder.setPendingPrice(price);
        holder.setPendingDuration(duration);

        Inventory inv = Bukkit.createInventory(holder, 27, mm("&5&lMise en vente"));
        ItemStack fill = item(Material.BLACK_STAINED_GLASS_PANE, " ", null, 0);
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);

        String currency = manager.getEconomy() != null ? manager.getEconomy().currencyNamePlural() : "";
        double fee = plugin.getConfig().getDouble("hdv.listing-fee", 10.0);

        // Durée : slots 0,1,2
        inv.setItem(0, item(Material.RED_STAINED_GLASS_PANE,  "&c- 1h",  List.of("&7Réduire la durée"), 0));
        inv.setItem(1, item(Material.CLOCK, "&7Durée : &e" + duration + "h",
                List.of("&7Min: 1h — Max: " + plugin.getConfig().getInt("hdv.max-duration-hours", 168) + "h"), 0));
        inv.setItem(2, item(Material.LIME_STAINED_GLASS_PANE, "&a+ 1h",  List.of("&7Augmenter la durée"), 0));

        // Item affiché au centre
        inv.setItem(13, item.clone());

        // Prix : slots 6,7,8
        inv.setItem(6, item(Material.RED_STAINED_GLASS_PANE,  "&c- Prix",
                List.of("&eClique &7-1", "&eShift+clic &7-100"), 0));
        inv.setItem(7, item(Material.SUNFLOWER, "&7Prix : &e" + String.format("%.2f", price) + " " + currency,
                List.of("&eClique &7+1/-1", "&eShift &7+100/-100"), 0));
        inv.setItem(8, item(Material.LIME_STAINED_GLASS_PANE, "&a+ Prix",
                List.of("&eClique &7+1", "&eShift+clic &7+100"), 0));

        // Annuler / Confirmer
        inv.setItem(18, item(Material.BARRIER, "&c✖ Annuler", null, 0));
        inv.setItem(26, item(Material.LIME_STAINED_GLASS_PANE, "&a✔ Mettre en vente",
                List.of("&7Item : &f" + getDisplayName(item),
                        "&7Prix : &e" + String.format("%.2f", price) + " " + currency,
                        "&7Durée : &e" + duration + "h",
                        "&7Frais : &c" + String.format("%.2f", fee) + " " + currency,
                        "", "&aCliquer pour confirmer"), 0));

        player.openInventory(inv);
        return holder;
    }

    // ── Utils ─────────────────────────────────────────────────────────────

    public static int[] getListingSlots() { return LISTING_SLOTS; }

    public String getDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        return item.getType().name();
    }

    public ItemStack item(Material mat, String name, List<String> lore, int cmd) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(mm(name));
        if (lore != null && !lore.isEmpty())
            meta.lore(lore.stream().map(this::mm).toList());
        if (cmd > 0) meta.setCustomModelData(cmd);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                          ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component mm(String s) {
        return MiniMessage.miniMessage().deserialize(
                s.replace("&0","<black>").replace("&8","<dark_gray>")
                 .replace("&5","<dark_purple>").replace("&6","<gold>")
                 .replace("&e","<yellow>").replace("&a","<green>")
                 .replace("&c","<red>").replace("&b","<aqua>")
                 .replace("&7","<gray>").replace("&f","<white>")
                 .replace("&l","<bold>").replace("&r","<reset>"));
    }
}
