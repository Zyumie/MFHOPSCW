package fr.zyumie.customitems.command;

import fr.zyumie.customitems.CustomItems;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GiveItemCommand implements CommandExecutor, TabCompleter {

    private final CustomItems plugin;

    public GiveItemCommand(CustomItems plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("customitems.admin")) {
            send(sender, plugin.getConfig().getString("messages.no-permission", "")); return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            send(sender, "<gold>/items give <joueur> <item>"); return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            send(sender, plugin.getConfig().getString("messages.player-not-found", "")
                    .replace("{player}", args[1])); return true;
        }

        String itemName = args[2].toLowerCase();
        ItemStack[] items = plugin.getItemRegistry().resolveGiveItems(itemName);
        if (items == null) {
            send(sender, plugin.getConfig().getString("messages.item-not-found", "")
                    .replace("{item}", itemName)); return true;
        }

        for (ItemStack item : items) {
            if (item != null) target.getInventory().addItem(item);
        }

        send(sender, plugin.getConfig().getString("messages.give-success", "")
                .replace("{item}", itemName).replace("{player}", target.getName()));
        send(target, plugin.getConfig().getString("messages.give-received", "")
                .replace("{item}", itemName));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("customitems.admin")) return List.of();
        if (args.length == 1) return List.of("give");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        if (args.length == 3) return new ArrayList<>(plugin.getItemRegistry().getGiveNames())
                .stream().filter(n -> n.startsWith(args[2].toLowerCase())).sorted().toList();
        return List.of();
    }

    private void send(CommandSender s, String message) {
        String prefix = plugin.getConfig().getString("general.prefix", "<dark_gray>[<gold>Items<dark_gray>] <gray>");
        s.sendMessage(MiniMessage.miniMessage().deserialize(prefix + message));
    }
}
