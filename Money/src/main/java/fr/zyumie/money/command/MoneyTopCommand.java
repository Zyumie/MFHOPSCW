package fr.zyumie.money.command;

import fr.zyumie.money.Money;
import fr.zyumie.money.MoneyManager;
import fr.zyumie.money.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MoneyTopCommand implements CommandExecutor, TabCompleter {

    private final Money plugin;
    private final MoneyManager manager;

    public MoneyTopCommand(Money plugin, MoneyManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        int perPage = plugin.getConfig().getInt("moneytop.entries-per-page", 10);
        int page = 1;
        if (args.length >= 1) {
            try { page = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        page = Math.max(1, page);

        List<Map.Entry<String, Double>> all = manager.getTopBalances(1000);
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        page = Math.min(page, totalPages);

        List<Map.Entry<String, Double>> pageEntries =
                all.subList(Math.min((page - 1) * perPage, all.size()), Math.min(page * perPage, all.size()));

        String plural = plugin.getConfig().getString("economy.currency-plural", "Crédits");

        send(sender, msg("messages.moneytop-header")
                .replace("{page}", String.valueOf(page))
                .replace("{max}", String.valueOf(totalPages)));

        int rank = (page - 1) * perPage + 1;
        for (Map.Entry<String, Double> e : pageEntries) {
            send(sender, msg("messages.moneytop-entry")
                    .replace("{rank}", String.valueOf(rank++))
                    .replace("{player}", e.getKey())
                    .replace("{amount}", String.format(Locale.ROOT, "%.2f", e.getValue()))
                    .replace("{currency}", plural));
        }

        if (pageEntries.isEmpty()) {
            send(sender, "&7Aucune entrée.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return List.of();
    }

    private String msg(String path) { return plugin.getConfig().getString(path, ""); }
    private String prefix()         { return msg("messages.prefix"); }

    private void send(CommandSender s, String legacy) {
        Msg.send(s, prefix() + legacy);
    }
}
