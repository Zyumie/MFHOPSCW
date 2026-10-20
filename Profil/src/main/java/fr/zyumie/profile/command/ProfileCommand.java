package fr.zyumie.profile.command;

import fr.zyumie.profile.ProfileData;
import fr.zyumie.profile.gui.ProfileGuiManager;
import fr.zyumie.profile.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProfileCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final ProfileGuiManager guiManager;

    public ProfileCommand(JavaPlugin plugin, ProfileGuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("profile.admin")) {
                sender.sendMessage(mm(msg("messages.no-permission")));
                return true;
            }
            plugin.reloadConfig();
            guiManager.reload();
            sender.sendMessage(mm(msg("messages.reload-success")));
            return true;
        }

        OfflinePlayer target;

        if (args.length >= 1) {
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) {
                // args[0] est écrit tel quel (Component.text), jamais réinterprété comme du MiniMessage
                sender.sendMessage(mm(msg("messages.player-not-found")).append(Component.text(args[0])));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(mm(msg("messages.usage")));
            return true;
        }

        showProfile(sender, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission("profile.admin") && "reload".startsWith(prefix)) {
                suggestions.add("reload");
            }
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .forEach(suggestions::add);
            return suggestions;
        }
        return List.of();
    }

    private void showProfile(CommandSender viewer, OfflinePlayer target) {
        ProfileData data = computeProfileData(target);

        String mode = plugin.getConfig().getString("display.mode", "chat");
        if (mode.equalsIgnoreCase("gui") && viewer instanceof Player viewerPlayer) {
            guiManager.open(viewerPlayer, data);
        } else {
            showProfileChat(viewer, data);
        }
    }

    /**
     * Résout argent/faction/banque une seule fois pour ce /profile. Le résultat est ensuite
     * utilisé aussi bien par l'affichage chat que par le GUI, pour ne jamais interroger
     * Vault/FactionPlugin deux fois pour la même commande.
     */
    private ProfileData computeProfileData(OfflinePlayer target) {
        fr.zyumie.faction.Faction factionPlugin = getFactionPlugin();
        fr.zyumie.faction.model.Faction faction = (factionPlugin != null)
                ? factionPlugin.getFactionManager().getFactionOf(target.getUniqueId())
                : null;
        Economy eco = getEconomy();

        String money;
        boolean moneyFallback;
        if (eco == null) {
            money = msg("messages.no-economy");
            moneyFallback = true;
        } else {
            money = String.format(Locale.ROOT, "%.2f %s", eco.getBalance(target), eco.currencyNamePlural());
            moneyFallback = false;
        }

        String factionName;
        boolean factionFallback;
        if (factionPlugin == null) {
            factionName = msg("messages.no-faction-module");
            factionFallback = true;
        } else if (faction == null) {
            factionName = msg("messages.no-faction");
            factionFallback = true;
        } else {
            factionName = faction.getName();
            factionFallback = false;
        }

        String bank;
        boolean bankFallback;
        if (factionPlugin == null) {
            bank = msg("messages.no-faction-bank");
            bankFallback = true;
        } else if (faction == null) {
            bank = msg("messages.no-faction-selected");
            bankFallback = true;
        } else {
            bank = String.format(
                    Locale.ROOT, "%.2f%s",
                    faction.getBankBalance(),
                    eco != null ? " " + eco.currencyNamePlural() : ""
            );
            bankFallback = false;
        }

        return new ProfileData(target, target.isOnline(), money, moneyFallback, factionName, factionFallback, bank, bankFallback);
    }

    private void showProfileChat(CommandSender viewer, ProfileData data) {
        String name = data.target().getName() != null ? data.target().getName() : "Inconnu";
        Component status = mm(msg(data.online() ? "messages.status-online" : "messages.status-offline"));

        viewer.sendMessage(mm(msg("messages.header")));
        // Nom du joueur inséré via Placeholder.unparsed, jamais réinterprété comme du MiniMessage :
        // un pseudo ne peut pas contenir de balise, mais on reste cohérent avec le reste de la classe.
        viewer.sendMessage(mm(msg("messages.name-line-prefix") + msg("messages.name-format") + "<value>",
                        Placeholder.unparsed("value", name))
                .append(mm(msg("messages.separator")))
                .append(status));
        viewer.sendMessage(mm(msg("messages.spacer")));
        viewer.sendMessage(valueLine("messages.money-label", data.money(), data.moneyFallback()));
        viewer.sendMessage(valueLine("messages.faction-label", data.faction(), data.factionFallback()));
        viewer.sendMessage(valueLine("messages.bank-label", data.bank(), data.bankFallback()));
        viewer.sendMessage(mm(msg("messages.footer")));
    }

    /**
     * Construit une ligne "préfixe + label + couleur de valeur" (tout config, donc fiable),
     * puis insère la valeur elle-même en texte brut (Placeholder.unparsed), jamais reparsée.
     */
    private Component valueLine(String labelKey, String value, boolean fallback) {
        String colorKey = fallback ? "messages.value-fallback-color" : "messages.value-color";
        return mm(msg("messages.line-prefix") + msg(labelKey) + msg(colorKey) + "<value>",
                Placeholder.unparsed("value", value));
    }

    private String msg(String path) {
        return plugin.getConfig().getString(path, "");
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

    private Component mm(String s, TagResolver... resolvers) {
        return Msg.parse(s, resolvers);
    }
}
