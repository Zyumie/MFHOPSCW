package fr.zyumie.main.oxygen.command;

import fr.zyumie.main.oxygen.OxygenManager;
import fr.zyumie.main.oxygen.Oxygen;
import fr.zyumie.main.oxygen.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class OxygenCommand implements CommandExecutor, TabCompleter {

    private final Oxygen plugin;
    private final OxygenManager manager;

    public OxygenCommand(Oxygen plugin, OxygenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "&8[&bOxygène&8] &7");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oxygenplugin.admin")) {
            sender.sendMessage(ChatUtil.color(prefix() + "&cPas la permission."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(ChatUtil.color(prefix() + "&aConfig rechargée."));
            }

            case "get" -> {
                // /oxygen get <joueur>
                if (args.length < 2) { sendUsage(sender); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.color(prefix() + "&cJoueur introuvable ou hors ligne."));
                    return true;
                }
                double pct = manager.getPercent(target);
                boolean surface = manager.isSurface(target);
                sender.sendMessage(ChatUtil.color(prefix() + "&f" + target.getName()
                        + " &7— Oxygène: &b" + String.format("%.1f", pct) + "%"
                        + " &7| Zone: " + (surface ? "&cSurface" : "&aSous-Terre")));
            }

            case "set" -> {
                // /oxygen set <joueur> <valeur 0-100>
                if (args.length < 3) { sendUsage(sender); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.color(prefix() + "&cJoueur introuvable ou hors ligne."));
                    return true;
                }
                try {
                    double value = Double.parseDouble(args[2]);
                    manager.setPercent(target, value);
                    sender.sendMessage(ChatUtil.color(prefix() + "&aOxygène de &f" + target.getName()
                            + " &amisé à &b" + String.format("%.1f", value) + "%."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatUtil.color(prefix() + "&c'" + args[2] + "' n'est pas un nombre valide."));
                }
            }

            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatUtil.color(prefix() + "&7Commandes disponibles:"));
        sender.sendMessage(ChatUtil.color("  &b/oxygen reload &7— Recharge la config"));
        sender.sendMessage(ChatUtil.color("  &b/oxygen get <joueur> &7— Affiche le % d'oxygène d'un joueur"));
        sender.sendMessage(ChatUtil.color("  &b/oxygen set <joueur> <0-100> &7— Modifie le % d'oxygène"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "get", "set").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
