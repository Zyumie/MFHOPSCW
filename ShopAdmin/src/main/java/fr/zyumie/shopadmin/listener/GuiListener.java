package fr.zyumie.shopadmin.listener;

import fr.zyumie.shopadmin.ShopAdmin;
import fr.zyumie.shopadmin.ShopManager;
import fr.zyumie.shopadmin.gui.ShopGui;
import fr.zyumie.shopadmin.gui.ShopHolder;
import fr.zyumie.shopadmin.model.ShopCategory;
import fr.zyumie.shopadmin.model.ShopItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class GuiListener implements Listener {

    private final ShopAdmin plugin;
    private final ShopManager manager;
    private final ShopGui gui;

    public GuiListener(ShopAdmin plugin, ShopManager manager, ShopGui gui) {
        this.plugin  = plugin;
        this.manager = manager;
        this.gui     = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // Identification fiable via InventoryHolder — plus de comparaison de titre
        if (!(e.getInventory().getHolder() instanceof ShopHolder holder)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true); // bloque TOUS les clics dans nos GUIs

        if (e.getClickedInventory() == null) return;
        if (!e.getClickedInventory().equals(e.getInventory())) return; // ignore clics dans l'inv joueur

        int slot = e.getRawSlot();

        if (holder.getType() == ShopHolder.Type.MAIN) {
            handleMain(player, slot, holder, e.isRightClick(), e.isShiftClick());
        } else if (holder.getType() == ShopHolder.Type.QUANTITY) {
            handleQty(player, slot, holder);
        }
    }

    // ── GUI principal ─────────────────────────────────────────────────────

    private void handleMain(Player player, int slot, ShopHolder holder,
                            boolean rightClick, boolean shiftClick) {
        List<ShopCategory> cats = manager.getCategoryList();

        // Rangée 0 : catégories (slots 0-8)
        if (slot < 9 && slot < cats.size()) {
            holder.setCategoryId(cats.get(slot).getId());
            holder.setPage(0);
            gui.open(player, holder.getCategoryId(), 0);
            return;
        }

        // Navigation rangée 5
        if (slot == 47) { player.closeInventory(); return; }
        if (slot == 45 && holder.getPage() > 0) {
            holder.setPage(holder.getPage() - 1);
            gui.open(player, holder.getCategoryId(), holder.getPage()); return;
        }
        if (slot == 53) {
            holder.setPage(holder.getPage() + 1);
            gui.open(player, holder.getCategoryId(), holder.getPage()); return;
        }

        // Zone items
        int[] itemSlots = ShopGui.getItemSlots();
        for (int i = 0; i < itemSlots.length; i++) {
            if (itemSlots[i] != slot) continue;

            ShopCategory cat = manager.getCategories().get(holder.getCategoryId());
            if (cat == null) return;
            List<ShopItem> items = cat.getItemsSorted();
            int idx = holder.getPage() * itemSlots.length + i;
            if (idx >= items.size()) return;

            ShopItem item = items.get(idx);
            boolean buying = !rightClick;

            if (shiftClick) {
                // Sélecteur de quantité
                gui.openQuantitySelector(player, item, buying, holder);
            } else {
                // Achat/vente x1 direct
                process(player, item, buying, 1, holder);
            }
            return;
        }
    }

    // ── Sélecteur de quantité ─────────────────────────────────────────────

    private void handleQty(Player player, int slot, ShopHolder holder) {
        if (slot == 22) {
            // Annuler → retour au shop
            gui.open(player, holder.getCategoryId(), holder.getPage()); return;
        }

        List<Integer> amounts = plugin.getConfig().getIntegerList(
                holder.isBuying() ? "shop.buy-amounts" : "shop.sell-amounts");
        if (amounts.isEmpty()) amounts = List.of(1, 8, 16, 32, 64);

        int[] btnSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(amounts.size(), btnSlots.length); i++) {
            if (btnSlots[i] != slot) continue;
            process(player, holder.getPendingItem(), holder.isBuying(), amounts.get(i), holder);
            gui.open(player, holder.getCategoryId(), holder.getPage());
            return;
        }
    }

    // ── Transaction ───────────────────────────────────────────────────────

    private void process(Player player, ShopItem item, boolean buying,
                         int amount, ShopHolder holder) {
        ShopManager.TransactionResult result = buying
                ? manager.buy(player, item, amount)
                : manager.sell(player, item, amount);

        String currency = manager.getEconomy() != null
                ? manager.getEconomy().currencyNamePlural() : "";
        double price = (buying ? item.getBuyPrice() : item.getSellPrice()) * amount;

        String msgKey;
        String msg;
        switch (result) {
            case SUCCESS -> {
                msgKey = buying ? "messages.buy-success" : "messages.sell-success";
                msg = plugin.getConfig().getString(msgKey, "")
                        .replace("{amount}", String.valueOf(amount))
                        .replace("{item}", item.getName())
                        .replace("{price}", String.format("%.2f", price) + " " + currency);
                send(player, msg);
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> gui.open(player, holder.getCategoryId(), holder.getPage()), 1L);
            }
            case INSUFFICIENT_FUNDS ->
                send(player, plugin.getConfig().getString("messages.insufficient-funds", "")
                        .replace("{price}", String.format("%.2f", price) + " " + currency));
            case INSUFFICIENT_STOCK ->
                send(player, plugin.getConfig().getString("messages.insufficient-stock", "")
                        .replace("{stock}", item.isUnlimitedStock() ? "∞" : String.valueOf(item.getStock())));
            case INVENTORY_FULL ->
                send(player, plugin.getConfig().getString("messages.inventory-full", ""));
            case NOT_ENOUGH_ITEMS ->
                send(player, plugin.getConfig().getString("messages.not-enough-items", "")
                        .replace("{item}", item.getName()));
            case DISABLED ->
                send(player, plugin.getConfig().getString("messages.sell-disabled", ""));
            default -> {}
        }
    }

    private void send(Player p, String legacy) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        p.sendMessage(MiniMessage.miniMessage().deserialize(
                (prefix + legacy)
                        .replace("&8","<dark_gray>").replace("&6","<gold>")
                        .replace("&e","<yellow>").replace("&a","<green>")
                        .replace("&c","<red>").replace("&7","<gray>")));
    }
}
