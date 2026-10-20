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

import java.util.List;
import java.util.Locale;

public class EcoCommand implements CommandExecutor, TabCompleter {

    private final Money plugin;
    private final MoneyManager manager;

    public EcoCommand(Money plugin, MoneyManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("money.admin")) {
            send(sender, msg("messages.no-permission")); return true;
        }
        if (args.length < 3) {
            send(sender, "<red>Usage: /eco <give|set|take> <joueur> <montant>"); return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String targetName = args[1];
        double amount;

        try { amount = Double.parseDouble(args[2].replace(",", ".")); }
        catch (NumberFormatException e) { send(sender, msg("messages.invalid-amount")); return true; }

        if (amount < 0) { send(sender, msg("messages.invalid-amount")); return true; }

        // Cherche online d'abord, puis offline
        OfflinePlayer target = Bukkit.getPlayer(targetName);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null) { send(sender, msg("messages.player-not-found").replace("{player}", targetName)); return true; }

        String displayName = target.getName() != null ? target.getName() : targetName;
        String plural = plugin.getConfig().getString("economy.currency-plural", "Crédits");
        String amountStr = String.format(Locale.ROOT, "%.2f", amount);

        switch (action) {
            case "give" -> {
                manager.deposit(target.getUniqueId(), amount);
                send(sender, msg("messages.eco-give")
                        .replace("{amount}", amountStr).replace("{player}", displayName).replace("{currency}", plural));
            }
            case "take" -> {
                manager.withdraw(target.getUniqueId(), amount);
                send(sender, msg("messages.eco-take")
                        .replace("{amount}", amountStr).replace("{player}", displayName).replace("{currency}", plural));
            }
            case "set" -> {
                manager.set(target.getUniqueId(), amount);
                send(sender, msg("messages.eco-set")
                        .replace("{amount}", amountStr).replace("{player}", displayName).replace("{currency}", plural));
            }
            default -> send(sender, "<red>Action inconnue. Usage: /eco <give|set|take> <joueur> <montant>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("give", "set", "take");
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }

    private String msg(String path) { return plugin.getConfig().getString(path, ""); }
    private String prefix()         { return msg("messages.prefix"); }

    private void send(CommandSender s, String message) {
        Msg.send(s, prefix() + message);
    }
}
