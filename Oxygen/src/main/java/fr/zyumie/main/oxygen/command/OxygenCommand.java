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
        return plugin.getConfig().getString("messages.prefix", "<dark_gray>[<aqua>Oxygène<dark_gray>] <gray>");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("oxygenplugin.admin")) {
            sender.sendMessage(ChatUtil.color(prefix() + "<red>Pas la permission."));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(ChatUtil.color(prefix() + "<green>Config rechargée."));
            }

            case "get" -> {
                // /oxygen get <joueur>
                if (args.length < 2) { sendUsage(sender); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.color(prefix() + "<red>Joueur introuvable ou hors ligne."));
                    return true;
                }
                double pct = manager.getPercent(target);
                boolean surface = manager.isSurface(target);
                sender.sendMessage(ChatUtil.color(prefix() + "<white>" + target.getName()
                        + " <gray>— Oxygène: <aqua>" + String.format("%.1f", pct) + "%"
                        + " <gray>| Zone: " + (surface ? "<red>Surface" : "<green>Sous-Terre")));
            }

            case "set" -> {
                // /oxygen set <joueur> <valeur 0-100>
                if (args.length < 3) { sendUsage(sender); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.color(prefix() + "<red>Joueur introuvable ou hors ligne."));
                    return true;
                }
                try {
                    double value = Double.parseDouble(args[2]);
                    manager.setPercent(target, value);
                    sender.sendMessage(ChatUtil.color(prefix() + "<green>Oxygène de <white>" + target.getName()
                            + " <green>misé à <aqua>" + String.format("%.1f", value) + "%."));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatUtil.color(prefix() + "<red>'" + args[2] + "' n'est pas un nombre valide."));
                }
            }

            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatUtil.color(prefix() + "<gray>Commandes disponibles:"));
        sender.sendMessage(ChatUtil.color("  <aqua>/oxygen reload <gray>— Recharge la config"));
        sender.sendMessage(ChatUtil.color("  <aqua>/oxygen get <joueur> <gray>— Affiche le % d'oxygène d'un joueur"));
        sender.sendMessage(ChatUtil.color("  <aqua>/oxygen set <joueur> <0-100> <gray>— Modifie le % d'oxygène"));
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
