package fr.zyumie.shopadmin.command;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.shopadmin.ShopAdmin;
import fr.zyumie.shopadmin.ShopManager;
import fr.zyumie.shopadmin.gui.ShopGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopAdmin plugin;
    private final ShopManager manager;
    private final ShopGui gui;

    public ShopCommand(ShopAdmin plugin, ShopManager manager, ShopGui gui) {
        this.plugin  = plugin;
        this.manager = manager;
        this.gui     = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("shopadmin.admin")) {
                send(sender, plugin.getConfig().getString("messages.no-permission", "")); return true;
            }
            plugin.reload();
            send(sender, plugin.getConfig().getString("messages.reload-success", "")); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Commande réservée aux joueurs."); return true; }

        // Le module peut être coupé temporairement (ex: storm-lockout du Main) sans que
        // la commande elle-même soit désenregistrée — donc on vérifie explicitement ici.
        if (!MainAPI.get().getModuleManager().isEnabled("shop-admin")) {
            send(player, plugin.getConfig().getString("messages.shop-closed", "&cLa boutique est fermée pour le moment."));
            return true;
        }

        gui.openHub(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("shopadmin.admin")) return List.of("reload");
        return List.of();
    }

    private void send(CommandSender s, String legacy) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        s.sendMessage(MiniMessage.miniMessage().deserialize(
                (prefix + legacy).replace("&8","<dark_gray>").replace("&6","<gold>")
                        .replace("&a","<green>").replace("&c","<red>").replace("&7","<gray>")));
    }
}
