package fr.zyumie.profile.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class ProfileCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public ProfileCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        OfflinePlayer target;

        if (args.length >= 1) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) {
                sender.sendMessage(mm("<red>Joueur introuvable : " + args[0]));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Usage: /profile <joueur>");
            return true;
        }

        showProfile(sender, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    private void showProfile(CommandSender viewer, OfflinePlayer target) {
        String name = target.getName() != null ? target.getName() : "Inconnu";
        String status = target.isOnline() ? "<green>● En ligne" : "<gray>○ Hors ligne";

        viewer.sendMessage(mm("<dark_gray>┌──────────────────────────────────"));
        viewer.sendMessage(mm("<dark_gray>│ <aqua><bold>" + name + "</bold> <dark_gray>— " + status));
        viewer.sendMessage(mm("<dark_gray>│"));
        viewer.sendMessage(mm("<dark_gray>│ <gray>💰 Argent       : <white>" + getMoneyString(target)));
        viewer.sendMessage(mm("<dark_gray>│ <gray>⚔ Faction       : <white>" + getFactionName(target.getUniqueId())));
        viewer.sendMessage(mm("<dark_gray>│ <gray>🏦 Bank faction : <white>" + getFactionBank(target.getUniqueId())));
        viewer.sendMessage(mm("<dark_gray>└──────────────────────────────────"));
    }

    private String getMoneyString(OfflinePlayer player) {
        Economy eco = getEconomy();
        if (eco == null) return "<gray>Aucun plugin économie";

        return String.format("%.2f %s", eco.getBalance(player), eco.currencyNamePlural());
    }

    private Economy getEconomy() {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);

        return rsp != null ? rsp.getProvider() : null;
    }

    private fr.zyumie.faction.Faction getFactionPlugin() {
        var p = Bukkit.getPluginManager().getPlugin("Faction");

        if (p instanceof fr.zyumie.faction.Faction fp && fp.isEnabled()) {
            return fp;
        }

        return null;
    }

    private String getFactionName(UUID uuid) {
        fr.zyumie.faction.Faction fp = getFactionPlugin();

        if (fp == null) {
            return "<gray>Module faction inactif";
        }

        fr.zyumie.faction.model.Faction faction = fp.getFactionManager().getFactionOf(uuid);

        return faction != null ? faction.getName() : "<gray>Aucune";
    }

    private String getFactionBank(UUID uuid) {
        fr.zyumie.faction.Faction fp = getFactionPlugin();

        if (fp == null) {
            return "<gray>N/A";
        }

        fr.zyumie.faction.model.Faction faction = fp.getFactionManager().getFactionOf(uuid);

        if (faction == null) {
            return "<gray>Aucune faction";
        }

        Economy eco = getEconomy();

        return String.format(
                "%.2f%s",
                faction.getBankBalance(),
                eco != null ? " " + eco.currencyNamePlural() : ""
        );
    }

    private Component mm(String s) {
        return MiniMessage.miniMessage().deserialize(s);
    }
}