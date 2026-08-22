package fr.zyumie.money.command;

import fr.zyumie.money.Money;
import fr.zyumie.money.MoneyManager;
import fr.zyumie.money.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class PayCommand implements CommandExecutor, TabCompleter {

    private final Money plugin;
    private final MoneyManager manager;

    public PayCommand(Money plugin, MoneyManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Commande réservée aux joueurs."); return true; }
        if (args.length < 2) { send(player, "&cUsage: /pay <joueur> <montant>"); return true; }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) { send(player, msg("messages.player-not-found").replace("{player}", args[0])); return true; }
        if (target.equals(player)) { send(player, msg("messages.pay-self")); return true; }

        double amount;
        try { amount = Double.parseDouble(args[1].replace(",", ".")); }
        catch (NumberFormatException e) { send(player, msg("messages.invalid-amount")); return true; }

        double min = plugin.getConfig().getDouble("economy.min-transaction", 0.5);
        double max = plugin.getConfig().getDouble("economy.max-balance", 999_999_999.0);

        if (amount < min) {
            send(player, msg("messages.pay-min").replace("{min}", fmt(min)).replace("{currency}", plural())); return true;
        }

        double senderBalance = manager.getBalance(player.getUniqueId());
        if (senderBalance < amount) {
            send(player, msg("messages.pay-insufficient")
                    .replace("{balance}", fmt(senderBalance)).replace("{currency}", plural())); return true;
        }

        double targetBalance = manager.getBalance(target.getUniqueId());
        if (targetBalance + amount > max) {
            send(player, msg("messages.pay-max-balance")
                    .replace("{max}", fmt(max)).replace("{currency}", plural())); return true;
        }

        manager.withdraw(player.getUniqueId(), amount);
        manager.deposit(target.getUniqueId(), amount);

        String amountStr = fmt(amount);
        send(player, msg("messages.pay-success")
                .replace("{amount}", amountStr).replace("{player}", target.getName()).replace("{currency}", plural()));
        send(target, msg("messages.pay-received")
                .replace("{amount}", amountStr).replace("{player}", player.getName()).replace("{currency}", plural()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }

    private String msg(String path) { return plugin.getConfig().getString(path, ""); }
    private String plural()         { return plugin.getConfig().getString("economy.currency-plural", "Crédits"); }
    private String fmt(double v)    { return String.format(Locale.ROOT, "%.2f", v); }
    private String prefix()         { return msg("messages.prefix"); }

    private void send(Player p, String legacy) {
        Msg.send(p, prefix() + legacy);
    }
}
