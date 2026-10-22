package fr.zyumie.hdv.command;

import fr.zyumie.hdv.Hdv;
import fr.zyumie.hdv.HdvManager;
import fr.zyumie.hdv.gui.HdvGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class HdvCommand implements CommandExecutor, TabCompleter {

    private final Hdv plugin;
    private final HdvManager manager;
    private final HdvGui gui;

    public HdvCommand(Hdv plugin, HdvManager manager, HdvGui gui) {
        this.plugin  = plugin;
        this.manager = manager;
        this.gui     = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs."); return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase() : "open";
        switch (sub) {
            case "open" -> gui.openMain(player, false, 0);
            case "claim" -> {
                if (!manager.hasPendingClaims(player.getUniqueId())) {
                    send(player, plugin.getConfig().getString("messages.claim-nothing", "")); return true;
                }
                send(player, plugin.getConfig().getString(
                        manager.claimPending(player) ? "messages.claim-success" : "messages.inventory-full", ""));
            }
            case "reload" -> {
                if (!sender.hasPermission("hdv.admin")) {
                    send(player, plugin.getConfig().getString("messages.no-permission", "")); return true;
                }
                plugin.reload();
                send(player, "<green>HDV rechargé.");
            }
            default -> send(player, "<red>Usage: /hdv [claim|reload]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("open", "claim", "reload");
        return List.of();
    }

    private void send(Player p, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        p.sendMessage(MiniMessage.miniMessage().deserialize(prefix + message));
    }
}
