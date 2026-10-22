package fr.zyumie.shopadmin.listener;

import fr.zyumie.shopadmin.ShopAdmin;
import fr.zyumie.shopadmin.ShopManager;
import fr.zyumie.shopadmin.gui.ShopGui;
import fr.zyumie.shopadmin.gui.ShopHolder;
import fr.zyumie.shopadmin.model.ShopCategory;
import fr.zyumie.shopadmin.model.ShopItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
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
        if (!(e.getInventory().getHolder() instanceof ShopHolder holder)) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        e.setCancelled(true);

        if (e.getClickedInventory() == null) return;
        if (!e.getClickedInventory().equals(e.getInventory())) return;

        int slot = e.getRawSlot();

        if (holder.getType() == ShopHolder.Type.HUB) {
            handleHub(player, slot);
        } else if (holder.getType() == ShopHolder.Type.MAIN) {
            handleMain(player, slot, holder, e.isRightClick(), e.isShiftClick());
        } else if (holder.getType() == ShopHolder.Type.QUANTITY) {
            handleQty(player, slot, holder);
        }
    }

    private void handleHub(Player player, int slot) {
        FileConfiguration guiConfig = plugin.getGuiConfig();
        List<ShopCategory> cats = manager.getCategoryList();

        int[] catSlots = ShopGui.getHubCategorySlots(guiConfig);
        for (int i = 0; i < catSlots.length && i < cats.size(); i++) {
            if (catSlots[i] == slot) {
                gui.open(player, cats.get(i).getId(), 0);
                return;
            }
        }

        int closeSlot = guiConfig.getInt("hub.navigation.close.slot", 22);
        if (slot == closeSlot) { player.closeInventory(); }
    }

    private void handleMain(Player player, int slot, ShopHolder holder,
                            boolean rightClick, boolean shiftClick) {
        FileConfiguration guiConfig = plugin.getGuiConfig();

        int backSlot = guiConfig.getInt("main-gui.navigation.back.slot", 46);
        int prevSlot = guiConfig.getInt("main-gui.navigation.prev-page.slot", 45);
        int nextSlot = guiConfig.getInt("main-gui.navigation.next-page.slot", 53);
        int closeSlot = guiConfig.getInt("main-gui.navigation.close.slot", 49);

        if (slot == backSlot) { gui.openHub(player); return; }
        if (slot == closeSlot) { player.closeInventory(); return; }
        if (slot == prevSlot && holder.getPage() > 0) {
            holder.setPage(holder.getPage() - 1);
            gui.open(player, holder.getCategoryId(), holder.getPage()); return;
        }
        if (slot == nextSlot) {
            holder.setPage(holder.getPage() + 1);
            gui.open(player, holder.getCategoryId(), holder.getPage()); return;
        }

        int[] itemSlots = ShopGui.getItemSlots(guiConfig);
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
                gui.openQuantitySelector(player, item, buying, holder);
            } else {
                process(player, item, buying, 1, holder);
            }
            return;
        }
    }

    private void handleQty(Player player, int slot, ShopHolder holder) {
        int cancelSlot = plugin.getGuiConfig().getInt("quantity-selector.cancel.slot", 22);

        if (slot == cancelSlot) {
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

    private void process(Player player, ShopItem item, boolean buying, int amount, ShopHolder holder) {
        ShopManager.TransactionResult result = buying
                ? manager.buy(player, item, amount)
                : manager.sell(player, item, amount);

        String currency = manager.getEconomy() != null ? manager.getEconomy().currencyNamePlural() : "";
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
            case INSUFFICIENT_FUNDS -> send(player, plugin.getConfig().getString("messages.insufficient-funds", "").replace("{price}", String.format("%.2f", price) + " " + currency));
            case INSUFFICIENT_STOCK -> send(player, plugin.getConfig().getString("messages.insufficient-stock", "").replace("{stock}", item.isUnlimitedStock() ? "∞" : String.valueOf(item.getStock())));
            case INVENTORY_FULL -> send(player, plugin.getConfig().getString("messages.inventory-full", ""));
            case NOT_ENOUGH_ITEMS -> send(player, plugin.getConfig().getString("messages.not-enough-items", "").replace("{item}", item.getName()));
            case DISABLED -> send(player, plugin.getConfig().getString("messages.sell-disabled", ""));
            case NO_ECONOMY -> send(player, plugin.getConfig().getString("messages.no-economy", ""));
            case DUPLICATE -> send(player, plugin.getConfig().getString("messages.duplicate-purchase", ""));
            default -> {}
        }
    }

    private void send(Player p, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        p.sendMessage(MiniMessage.miniMessage().deserialize(prefix + message));
    }
}