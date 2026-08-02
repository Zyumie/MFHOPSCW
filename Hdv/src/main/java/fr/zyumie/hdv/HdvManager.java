package fr.zyumie.hdv;

import fr.zyumie.hdv.model.HdvListing;
import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HdvManager {

    private static final String NS_LISTING  = "hdv_listing";
    private static final String NS_CLAIMS   = "hdv_claims";

    private final Hdv plugin;
    private final Map<String, HdvListing> listings = new ConcurrentHashMap<>();
    // UUID joueur → liste d'ItemStacks à récupérer (items non vendus / expirés)
    private final Map<UUID, List<ItemStack>> pendingClaims = new ConcurrentHashMap<>();
    private Economy economy;

    public HdvManager(Hdv plugin) {
        this.plugin = plugin;
    }

    // ── Init ─────────────────────────────────────────────────────────────

    public void init() {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();
        else plugin.getLogger().severe("Vault Economy introuvable!");
        loadAll();
        startExpiryTask();
    }

    // ── Persistence ───────────────────────────────────────────────────────

    public void loadAll() {
        DataStore store = MainAPI.get().getDataStore();
        for (String key : store.listKeys(NS_LISTING)) {
            try {
                Map<String, Object> raw = store.load(NS_LISTING, key);
                if (raw == null) continue;
                HdvListing listing = deserializeListing(raw);
                if (listing != null && listing.isActive()) listings.put(listing.getId(), listing);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur chargement listing " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("HDV: " + listings.size() + " annonce(s) active(s) chargée(s).");
    }

    public void saveAll() {
        listings.values().forEach(this::saveListing);
    }

    private void saveListing(HdvListing listing) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("id",          listing.getId());
            data.put("seller",      listing.getSellerUuid().toString());
            data.put("sellerName",  listing.getSellerName());
            data.put("price",       listing.getPrice());
            data.put("createdAt",   listing.getCreatedAt());
            data.put("expiresAt",   listing.getExpiresAt());
            data.put("sold",        listing.isSold());
            data.put("cancelled",   listing.isCancelled());
            data.put("item",        serializeItem(listing.getItem()));
            MainAPI.get().getDataStore().save(NS_LISTING, listing.getId(), data);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur sauvegarde listing: " + e.getMessage());
        }
    }

    private HdvListing deserializeListing(Map<String, Object> raw) throws Exception {
        String id         = String.valueOf(raw.get("id"));
        UUID seller       = UUID.fromString(String.valueOf(raw.get("seller")));
        String sellerName = String.valueOf(raw.get("sellerName"));
        double price      = ((Number) raw.get("price")).doubleValue();
        long createdAt    = ((Number) raw.get("createdAt")).longValue();
        long expiresAt    = ((Number) raw.get("expiresAt")).longValue();
        boolean sold      = Boolean.parseBoolean(String.valueOf(raw.get("sold")));
        boolean cancelled = Boolean.parseBoolean(String.valueOf(raw.get("cancelled")));
        ItemStack item    = deserializeItem(String.valueOf(raw.get("item")));
        HdvListing l = new HdvListing(id, seller, sellerName, item, price, createdAt, expiresAt);
        l.setSold(sold);
        l.setCancelled(cancelled);
        return l;
    }

    // ── Item serialization ────────────────────────────────────────────────

    private String serializeItem(ItemStack item) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private ItemStack deserializeItem(String base64) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(base64);
        try (BukkitObjectInputStream bois = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ItemStack) bois.readObject();
        }
    }

    // ── Listings CRUD ─────────────────────────────────────────────────────

    public enum CreateResult { SUCCESS, MAX_LISTINGS, NO_ECONOMY, INSUFFICIENT_FUNDS, INVALID_PRICE }

    public CreateResult createListing(Player seller, ItemStack item, double price, int durationHours) {
        if (economy == null) return CreateResult.NO_ECONOMY;

        double min = plugin.getConfig().getDouble("hdv.min-price", 1.0);
        double max = plugin.getConfig().getDouble("hdv.max-price", 999_999_999.0);
        if (price < min || price > max) return CreateResult.INVALID_PRICE;

        int maxListings = plugin.getConfig().getInt("hdv.max-listings-per-player", 10);
        long count = listings.values().stream()
                .filter(l -> l.getSellerUuid().equals(seller.getUniqueId()) && l.isActive())
                .count();
        if (count >= maxListings) return CreateResult.MAX_LISTINGS;

        double fee = plugin.getConfig().getDouble("hdv.listing-fee", 10.0);
        if (!economy.has(seller, fee)) return CreateResult.INSUFFICIENT_FUNDS;

        economy.withdrawPlayer(seller, fee);

        long now      = System.currentTimeMillis();
        long expires  = now + (long) durationHours * 3600_000L;
        String id     = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HdvListing listing = new HdvListing(id, seller.getUniqueId(), seller.getName(),
                item, price, now, expires);
        listings.put(id, listing);
        saveListing(listing);
        return CreateResult.SUCCESS;
    }

    public enum BuyResult { SUCCESS, NOT_FOUND, OWN_LISTING, NO_ECONOMY, INSUFFICIENT_FUNDS, INVENTORY_FULL }

    public BuyResult buy(Player buyer, String listingId) {
        if (economy == null) return BuyResult.NO_ECONOMY;
        HdvListing listing = listings.get(listingId);
        if (listing == null || !listing.isActive()) return BuyResult.NOT_FOUND;
        if (listing.getSellerUuid().equals(buyer.getUniqueId())) return BuyResult.OWN_LISTING;
        if (!economy.has(buyer, listing.getPrice())) return BuyResult.INSUFFICIENT_FUNDS;
        if (buyer.getInventory().firstEmpty() == -1) return BuyResult.INVENTORY_FULL;

        economy.withdrawPlayer(buyer, listing.getPrice());

        double taxPercent = plugin.getConfig().getDouble("hdv.tax-percent", 5.0);
        double tax = listing.getPrice() * taxPercent / 100.0;
        double sellerAmount = listing.getPrice() - tax;

        // Crédite le vendeur
        OfflinePlayerHelper.deposit(economy, listing.getSellerUuid(), sellerAmount);
        buyer.getInventory().addItem(listing.getItem());
        listing.setSold(true);
        listings.remove(listingId);
        saveListing(listing);

        // Notification vendeur si en ligne
        Player seller = Bukkit.getPlayer(listing.getSellerUuid());
        if (seller != null) {
            String msg = plugin.getConfig().getString("messages.listing-sold", "")
                    .replace("{item}", itemName(listing.getItem()))
                    .replace("{price}", fmt(sellerAmount))
                    .replace("{tax}", fmt(tax));
            seller.sendMessage(mm(prefix() + msg));
        }
        return BuyResult.SUCCESS;
    }

    public boolean cancelListing(Player player, String listingId) {
        HdvListing listing = listings.get(listingId);
        if (listing == null || !listing.isActive()) return false;
        if (!listing.getSellerUuid().equals(player.getUniqueId())) return false;

        if (player.getInventory().firstEmpty() == -1) {
            addPendingClaim(player.getUniqueId(), listing.getItem());
        } else {
            player.getInventory().addItem(listing.getItem());
        }
        listing.setCancelled(true);
        listings.remove(listingId);
        saveListing(listing);
        return true;
    }

    public void addPendingClaim(UUID uuid, ItemStack item) {
        pendingClaims.computeIfAbsent(uuid, u -> new ArrayList<>()).add(item.clone());
        saveClaims(uuid);
    }

    public boolean claimPending(Player player) {
        List<ItemStack> items = pendingClaims.get(player.getUniqueId());
        if (items == null || items.isEmpty()) return false;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : items) {
            if (player.getInventory().firstEmpty() == -1) {
                remaining.add(item);
            } else {
                player.getInventory().addItem(item);
            }
        }
        if (remaining.isEmpty()) {
            pendingClaims.remove(player.getUniqueId());
            MainAPI.get().getDataStore().delete(NS_CLAIMS, player.getUniqueId().toString());
        } else {
            pendingClaims.put(player.getUniqueId(), remaining);
            saveClaims(player.getUniqueId());
        }
        return true;
    }

    public boolean hasPendingClaims(UUID uuid) {
        List<ItemStack> items = pendingClaims.get(uuid);
        return items != null && !items.isEmpty();
    }

    private void saveClaims(UUID uuid) {
        List<ItemStack> items = pendingClaims.getOrDefault(uuid, List.of());
        try {
            Map<String, Object> data = new HashMap<>();
            for (int i = 0; i < items.size(); i++) {
                data.put("item_" + i, serializeItem(items.get(i)));
            }
            MainAPI.get().getDataStore().save(NS_CLAIMS, uuid.toString(), data);
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur sauvegarde claims: " + e.getMessage());
        }
    }

    // ── Expiry task ───────────────────────────────────────────────────────

    private void startExpiryTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            List<HdvListing> expired = listings.values().stream()
                    .filter(l -> l.isActive() && l.isExpired()).toList();
            for (HdvListing l : expired) {
                listings.remove(l.getId());
                l.setCancelled(true);
                saveListing(l);
                // Restitue l'item (claim)
                addPendingClaim(l.getSellerUuid(), l.getItem());
                // Notifie si en ligne
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(l.getSellerUuid());
                    if (p != null) p.sendMessage(mm(prefix() +
                            plugin.getConfig().getString("messages.listing-expired", "")
                                    .replace("{item}", itemName(l.getItem()))));
                });
            }
        }, 600L, 1200L); // check toutes les 60s
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public List<HdvListing> getActiveListings() {
        return listings.values().stream().filter(HdvListing::isActive)
                .sorted(Comparator.comparingLong(HdvListing::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<HdvListing> getPlayerListings(UUID uuid) {
        return listings.values().stream()
                .filter(l -> l.getSellerUuid().equals(uuid) && l.isActive())
                .collect(Collectors.toList());
    }

    public HdvListing getListing(String id) { return listings.get(id); }
    public Economy getEconomy()             { return economy; }

    // ── Utils ─────────────────────────────────────────────────────────────

    private String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(item.getItemMeta().displayName());
        }
        return item.getType().name();
    }

    private String fmt(double v) { return String.format("%.2f", v); }
    private String prefix()      { return plugin.getConfig().getString("messages.prefix", ""); }

    private net.kyori.adventure.text.Component mm(String s) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                s.replace("&8","<dark_gray>").replace("&5","<dark_purple>")
                 .replace("&a","<green>").replace("&c","<red>")
                 .replace("&e","<yellow>").replace("&7","<gray>"));
    }

    // Helper pour déposer de l'argent chez un joueur offline
    static class OfflinePlayerHelper {
        static void deposit(Economy eco, UUID uuid, double amount) {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            eco.depositPlayer(op, amount);
        }
    }
}
