package fr.zyumie.customitems.handler.armor;

import fr.zyumie.customitems.CustomItems; // <-- Import CustomItems
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class StormArmorHandler {

    private final CustomItems plugin; // <-- CustomItems au lieu de JavaPlugin

    public StormArmorHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    public boolean isImmune(Player player) {
        if (!plugin.getCustomArmorConfig().getBoolean("armors.storm.enabled", true)) return false;
        boolean requireFull = plugin.getCustomArmorConfig().getBoolean("armors.storm.require-full-set", true);
        int count = countPieces(player, ItemRegistry.STORM_ARMOR);
        return requireFull ? count == 4 : count >= 1;
    }

    private int countPieces(Player player, String id) {
        int c = 0;
        for (ItemStack piece : player.getInventory().getArmorContents())
            if (ItemBuilder.isCustomItem(plugin, piece, id)) c++;
        return c;
    }
}