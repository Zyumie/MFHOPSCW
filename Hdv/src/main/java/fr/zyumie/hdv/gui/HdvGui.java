package fr.zyumie.hdv.gui;

import fr.zyumie.hdv.HdvManager;
import fr.zyumie.hdv.model.HdvListing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
    private final FileConfiguration config;

    // Slots d'annonces (rangées 1-4, colonnes 1-7 = 28 annonces/page)
    private static final int[] LISTING_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    public HdvGui(JavaPlugin plugin, HdvManager manager, FileConfiguration config) {
        this.plugin  = plugin;
        this.manager = manager;
        this.config  = config;
    }

    /**
     * Applique le fond (fill) dynamiquement si "enabled: true" dans le Gui.yml
     */
    private void applyFill(Inventory inv, String path) {
        ConfigurationSection fillSec = config.getConfigurationSection(path + ".fill");
        if (fillSec == null || !fillSec.getBoolean("enabled", true)) {
            return; // Ne fait rien si désactivé ou absent
        }

        Material mat = Material.valueOf(fillSec.getString("material", "PURPLE_STAINED_GLASS_PANE"));
        String name = fillSec.getString("name", " ");
        int cmd = fillSec.getInt("custom-model-data", 0);

        ItemStack fillItem = item(mat, name, null, cmd);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, fillItem);
        }
    }

    // ── Main GUI ──────────────────────────────────────────────────────────

    public HdvHolder openMain(Player player, boolean myListings, int page) {
        HdvHolder holder = new HdvHolder(HdvHolder.Type.MAIN);
        holder.setMyListings(myListings);
        holder.setPage(page);

        // Lecture dynamique du titre depuis Gui.yml
        ConfigurationSection mainSec = config.getConfigurationSection("main-gui");
        String rawTitle = mainSec != null ? mainSec.getString("title", "&5&l✦ Hôtel des Ventes") : "&5&l✦ Hôtel des Ventes";

        Inventory inv = Bukkit.createInventory(holder, 54, mm(rawTitle));

        // Remplissage dynamique géré par le YAML
        applyFill(inv, "main-gui");

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

    private void buildFilterBar(Inventory inv) {
        inv.setItem(0, item(Material.NETHER_STAR,  "&f✦ Toutes les annonces",
                List.of("&7Afficher toutes les annonces"), 1001));
        inv.setItem(1, item(Material.CHEST, "&e⬡ Mes annonces",
                List.of("&7Voir et gérer tes annonces"), 1002));
        inv.setItem(2, item(Material.GOLD_INGOT, "&6✚ Mettre en vente",
                List.of("&7Mettre un item en vente", "&7Tiens l'item dans ta main principale", "&7et clique ici."), 1003));
        inv.setItem(3, item(Material.ENDER_CHEST, "&a⬇ Récupérer mes items",
                List.of("&7Récupérer les items des annonces", "&7expirées ou annulées."), 1004));
    }

    private void buildMainNav(Inventory inv, int page, int totalPages) {
        if (page > 0)
            inv.setItem(45, item(Material.ARROW, "&7◀ Page précédente", List.of("&7Aller à la page précédente"), 1010));
        if (page < totalPages - 1)
            inv.setItem(53, item(Material.ARROW, "&7Page suivante ▶", List.of("&7Aller à la page suivante"), 1012));
        inv.setItem(49, item(Material.MAP,
                "&7Page &e" + (page + 1) + "&7/&e" + totalPages, List.of("&7Navigation entre les pages"), 1011));
        inv.setItem(47, item(Material.BARRIER, "&c✖ Fermer", new ArrayList<>(), 0));
    }

    private ItemStack buildListingItem(HdvListing listing, Player viewer) {
        ItemStack base = listing.getItem().clone();
        ItemMeta meta = base.getItemMeta();
        String currency = manager.getEconomy() != null ? manager.getEconomy().currencyNamePlural() : "";

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.lore());
        lore.add(mm(""));
        lore.add(mm("&7Vendeur: &e" + listing.getSellerName()));
        lore.add(mm("&7Prix: &e" + String.format("%.2f", listing.getPrice()) + " " + currency));
        lore.add(mm("&7Expire dans: &e" + listing.getTimeRemaining()));
        lore.add(mm(""));
        if (listing.getSellerUuid().equals(viewer.getUniqueId())) {
            lore.add(mm("&eClique droit &7pour annuler (si ta liste)"));
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

        // Lecture dynamique du titre depuis Gui.yml
        ConfigurationSection confirmSec = config.getConfigurationSection("confirm-gui");
        String rawTitle = confirmSec != null ? confirmSec.getString("title", "&5&lConfirmer l'achat") : "&5&lConfirmer l'achat";

        Inventory inv = Bukkit.createInventory(holder, 27, mm(rawTitle));

        // Remplissage dynamique géré par le YAML
        applyFill(inv, "confirm-gui");

        String currency = manager.getEconomy() != null ? manager.getEconomy().currencyNamePlural() : "";
        String price    = String.format("%.2f", listing.getPrice()) + " " + currency;

        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, "&a✔ Confirmer l'achat",
                List.of("&7Item: &f" + getDisplayName(listing.getItem()),
                        "&7Prix: &e" + price,
                        "&7Taxe incluse dans le prix vendeur",
                        "",
                        "&aCliquer pour confirmer"), 1020));
        inv.setItem(13, listing.getItem().clone());
        inv.setItem(15, item(Material.RED_STAINED_GLASS_PANE, "&c✖ Annuler",
                List.of("&7Retour à la liste"), 1021));

        player.openInventory(inv);
        return holder;
    }

    // ── Create GUI ────────────────────────────────────────────────────────

    public HdvHolder openCreate(Player player, ItemStack item, double price, int duration) {
        HdvHolder holder = new HdvHolder(HdvHolder.Type.CREATE);
        holder.setPendingItem(item.clone());
        holder.setPendingPrice(price);
        holder.setPendingDuration(duration);

        // Lecture dynamique du titre depuis Gui.yml
        ConfigurationSection createSec = config.getConfigurationSection("create-gui");
        String rawTitle = createSec != null ? createSec.getString("title", "&5&lMise en vente") : "&5&lMise en vente";

        Inventory inv = Bukkit.createInventory(holder, 27, mm(rawTitle));

        // Remplissage dynamique géré par le YAML
        applyFill(inv, "create-gui");

        String currency = manager.getEconomy() != null ? manager.getEconomy().currencyNamePlural() : "";
        double fee = plugin.getConfig().getDouble("hdv.listing-fee", 10.0);

        // Durée : slots 9, 10, 11
        inv.setItem(9, item(Material.RED_STAINED_GLASS_PANE,  "&c- &71h",  null, 0));
        inv.setItem(10, item(Material.CLOCK, "&7Durée: &e" + duration + "h",
                List.of("&7Clique sur + ou - pour ajuster"), 0));
        inv.setItem(11, item(Material.LIME_STAINED_GLASS_PANE, "&a+ &71h",  null, 0));

        // Item affiché au centre
        inv.setItem(13, item.clone());

        // Prix : slots 18, 19, 20
        inv.setItem(18, item(Material.RED_STAINED_GLASS_PANE,  "&c- &7Prix",
                List.of("&eClique gauche &7-1", "&eShift+clic &7-100"), 0));
        inv.setItem(19, item(Material.SUNFLOWER, "&7Prix: &e" + String.format("%.2f", price) + " " + currency,
                List.of("&7Clique sur + ou - pour ajuster"), 1030));
        inv.setItem(20, item(Material.LIME_STAINED_GLASS_PANE, "&a+ &7Prix",
                List.of("&eClique gauche &7+1", "&eShift+clic &7+100"), 0));

        // Annuler / Confirmer
        inv.setItem(22, item(Material.BARRIER, "&c✖ Annuler", null, 0));
        inv.setItem(24, item(Material.LIME_STAINED_GLASS_PANE, "&a✔ Mettre en vente",
                List.of("&7Item: &f" + getDisplayName(item),
                        "&7Prix: &e" + String.format("%.2f", price) + " " + currency,
                        "&7Durée: &e" + duration + "h",
                        "&7Frais de création: &c" + String.format("%.2f", fee),
                        "",
                        "&aCliquer pour confirmer"), 1020));

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