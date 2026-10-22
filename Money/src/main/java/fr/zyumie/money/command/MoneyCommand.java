package fr.zyumie.money.command;

import fr.zyumie.money.Money;
import fr.zyumie.money.MoneyManager;
import fr.zyumie.money.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MoneyCommand implements CommandExecutor, TabCompleter {

    private final Money plugin;
    private final MoneyManager manager;

    public MoneyCommand(Money plugin, MoneyManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("money.admin")) {
                send(sender, msg("messages.no-permission")); return true;
            }
            plugin.reloadConfig();
            manager.invalidateTopCache();
            send(sender, msg("messages.reload-success"));
            return true;
        }

        if (args.length == 0) {
            // /money → propre solde
            if (!(sender instanceof Player player)) { sender.sendMessage("Usage: /money <joueur>"); return true; }
            double bal = manager.getBalance(player.getUniqueId());
            send(sender, msg("messages.balance")
                    .replace("{amount}", fmt(bal))
                    .replace("{currency}", bal == 1.0 ? singular() : plural()));
        } else {
            // /money <joueur>
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) { send(sender, msg("messages.player-not-found").replace("{player}", args[0])); return true; }
            double bal = manager.getBalance(target.getUniqueId());
            send(sender, msg("messages.balance-other")
                    .replace("{player}", target.getName() != null ? target.getName() : args[0])
                    .replace("{amount}", fmt(bal))
                    .replace("{currency}", bal == 1.0 ? singular() : plural()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission("money.admin") && "reload".startsWith(prefix)) {
                suggestions.add("reload");
            }
            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .forEach(suggestions::add);
            return suggestions;
        }
        return List.of();
    }

    private String msg(String path)  { return plugin.getConfig().getString(path, ""); }
    private String singular()        { return plugin.getConfig().getString("economy.currency-singular", "Crédit"); }
    private String plural()          { return plugin.getConfig().getString("economy.currency-plural", "Crédits"); }
    private String fmt(double v)     { return String.format(Locale.ROOT, "%.2f", v); }
    private String prefix()          { return msg("messages.prefix"); }

    private void send(CommandSender s, String message) {
        Msg.send(s, prefix() + message);
    }
}
