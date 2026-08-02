package fr.zyumie.hdv.listener;

import fr.zyumie.hdv.Hdv;
import fr.zyumie.hdv.HdvManager;
import fr.zyumie.hdv.gui.HdvGui;
import fr.zyumie.hdv.gui.HdvHolder;
import fr.zyumie.hdv.model.HdvListing;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class HdvGuiListener implements Listener {

    private final Hdv plugin;
    private final HdvManager manager;
    private final HdvGui gui;

    public HdvGuiListener(Hdv plugin, HdvManager manager, HdvGui gui) {
        this.plugin  = plugin;
        this.manager = manager;
        this.gui     = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof HdvHolder holder)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);
        if (e.getClickedInventory() == null) return;
        if (!e.getClickedInventory().equals(e.getInventory())) return;

        int slot = e.getRawSlot();

        switch (holder.getType()) {
            case MAIN        -> handleMain(player, slot, holder, e.isRightClick(), e.isShiftClick());
            case CONFIRM_BUY -> handleConfirm(player, slot, holder);
            case CREATE      -> handleCreate(player, slot, holder, e.isShiftClick());
        }
    }

    // ── Main GUI ──────────────────────────────────────────────────────────

    private void handleMain(Player player, int slot, HdvHolder holder,
                            boolean rightClick, boolean shiftClick) {
        // Filter bar
        if (slot == 0) { gui.openMain(player, false, 0); return; }
        if (slot == 1) { gui.openMain(player, true,  0); return; }
        if (slot == 2) {
            var hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) { send(player, "<red>Tiens l'item à vendre en main principale."); return; }
            int defDur = plugin.getConfig().getInt("hdv.default-duration-hours", 48);
            double minP = plugin.getConfig().getDouble("hdv.min-price", 1.0);
            HdvHolder createHolder = gui.openCreate(player, hand, minP, defDur);
            return;
        }
        if (slot == 3) {
            player.closeInventory();
            if (!manager.hasPendingClaims(player.getUniqueId())) {
                send(player, plugin.getConfig().getString("messages.claim-nothing", "")); return;
            }
            boolean ok = manager.claimPending(player);
            send(player, plugin.getConfig().getString(ok ? "messages.claim-success" : "messages.inventory-full", ""));
            return;
        }

        // Navigation
        if (slot == 47) { player.closeInventory(); return; }
        if (slot == 45 && holder.getPage() > 0) {
            gui.openMain(player, holder.isMyListings(), holder.getPage() - 1); return;
        }
        if (slot == 53) {
            gui.openMain(player, holder.isMyListings(), holder.getPage() + 1); return;
        }

        // Zone annonces
        for (int i = 0; i < HdvGui.getListingSlots().length; i++) {
            if (HdvGui.getListingSlots()[i] != slot) continue;

            List<HdvListing> listings = holder.isMyListings()
                    ? manager.getPlayerListings(player.getUniqueId())
                    : manager.getActiveListings();
            int idx = holder.getPage() * HdvGui.getListingSlots().length + i;
            if (idx >= listings.size()) return;
            HdvListing listing = listings.get(idx);

            if (rightClick && listing.getSellerUuid().equals(player.getUniqueId())) {
                boolean ok = manager.cancelListing(player, listing.getId());
                send(player, plugin.getConfig().getString(
                        ok ? "messages.listing-cancelled" : "messages.listing-not-found", ""));
                gui.openMain(player, holder.isMyListings(), holder.getPage());
            } else if (!listing.getSellerUuid().equals(player.getUniqueId())) {
                gui.openConfirm(player, listing, holder);
            }
            return;
        }
    }

    // ── Confirm GUI ───────────────────────────────────────────────────────

    private void handleConfirm(Player player, int slot, HdvHolder holder) {
        if (slot == 11) {
            // Annuler
            gui.openMain(player, holder.isMyListings(), holder.getPage()); return;
        }
        if (slot == 15) {
            // Confirmer achat
            HdvListing listing = holder.getPendingListing();
            var result = manager.buy(player, listing.getId());
            String currency = manager.getEconomy() != null ? manager.getEconomy().currencyNamePlural() : "";
            switch (result) {
                case SUCCESS -> send(player, plugin.getConfig().getString("messages.buy-success", "")
                        .replace("{item}", gui.getDisplayName(listing.getItem()))
                        .replace("{price}", String.format("%.2f", listing.getPrice()) + " " + currency));
                case INSUFFICIENT_FUNDS -> send(player, plugin.getConfig().getString("messages.buy-fail-funds", "")
                        .replace("{price}", String.format("%.2f", listing.getPrice()) + " " + currency));
                case INVENTORY_FULL -> send(player, plugin.getConfig().getString("messages.inventory-full", ""));
                default -> send(player, plugin.getConfig().getString("messages.listing-not-found", ""));
            }
            gui.openMain(player, false, 0);
        }
    }

    // ── Create GUI ────────────────────────────────────────────────────────

    private void handleCreate(Player player, int slot, HdvHolder holder, boolean shift) {
        int maxDur = plugin.getConfig().getInt("hdv.max-duration-hours", 168);
        double minP = plugin.getConfig().getDouble("hdv.min-price", 1.0);
        double maxP = plugin.getConfig().getDouble("hdv.max-price", 999_999_999.0);
        double step = shift ? 100.0 : 1.0;

        boolean refresh = true;
        switch (slot) {
            case 0  -> holder.setPendingDuration(Math.max(1,    holder.getPendingDuration() - 1));
            case 2  -> holder.setPendingDuration(Math.min(maxDur, holder.getPendingDuration() + 1));
            case 6  -> holder.setPendingPrice(Math.max(minP, holder.getPendingPrice() - step));
            case 8  -> holder.setPendingPrice(Math.min(maxP, holder.getPendingPrice() + step));
            case 18 -> { gui.openMain(player, false, 0); return; }
            case 26 -> {
                var hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir() || !hand.isSimilar(holder.getPendingItem())) {
                    send(player, "<red>Item introuvable en main principale."); return;
                }
                var result = manager.createListing(player, hand,
                        holder.getPendingPrice(), holder.getPendingDuration());
                String currency = manager.getEconomy() != null
                        ? manager.getEconomy().currencyNamePlural() : "";
                switch (result) {
                    case SUCCESS -> {
                        hand.setAmount(hand.getAmount() - holder.getPendingItem().getAmount());
                        send(player, plugin.getConfig().getString("messages.listing-created", "")
                                .replace("{item}", gui.getDisplayName(holder.getPendingItem()))
                                .replace("{price}", String.format("%.2f", holder.getPendingPrice()) + " " + currency)
                                .replace("{duration}", String.valueOf(holder.getPendingDuration())));
                        send(player, plugin.getConfig().getString("messages.listing-fee-charged", "")
                                .replace("{fee}", String.format("%.2f",
                                        plugin.getConfig().getDouble("hdv.listing-fee", 10.0)) + " " + currency));
                        gui.openMain(player, false, 0); return;
                    }
                    case MAX_LISTINGS -> send(player, plugin.getConfig().getString("messages.max-listings", "")
                            .replace("{max}", String.valueOf(plugin.getConfig().getInt("hdv.max-listings-per-player", 10))));
                    case INSUFFICIENT_FUNDS -> send(player, plugin.getConfig().getString("messages.buy-fail-funds", "")
                            .replace("{price}", String.format("%.2f",
                                    plugin.getConfig().getDouble("hdv.listing-fee", 10.0)) + " " + currency));
                    case INVALID_PRICE -> send(player, plugin.getConfig().getString("messages.invalid-price", "")
                            .replace("{min}", String.valueOf(minP)).replace("{max}", String.valueOf(maxP)));
                }
            }
            default -> refresh = false;
        }

        if (refresh) {
            final double price = holder.getPendingPrice();
            final int dur = holder.getPendingDuration();
            final var item = holder.getPendingItem();
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> gui.openCreate(player, item, price, dur), 1L);
        }
    }

    private void send(Player p, String legacy) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        p.sendMessage(MiniMessage.miniMessage().deserialize(
                (prefix + legacy)
                        .replace("&8","<dark_gray>").replace("&5","<dark_purple>")
                        .replace("&a","<green>").replace("&c","<red>")
                        .replace("&e","<yellow>").replace("&7","<gray>")));
    }
}
