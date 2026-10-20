package fr.zyumie.main.util;

import fr.zyumie.main.Main;
import fr.zyumie.main.module.Module;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AdminCommand implements CommandExecutor {

    private final Main plugin;

    public AdminCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("main.admin")) {
            sender.sendMessage(mm(prefix() + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(mm(prefix() + "<gray>Usage: /mainadmin <reload|list>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getModuleManager().reloadAll();
                // reloadAll() réactive tout selon config.yml sans savoir qu'une tempête
                // est en cours : on réapplique le verrou storm-lockout immédiatement après.
                plugin.getStormLockoutManager().reapplyIfStormActive();
                sender.sendMessage(mm(prefix() + plugin.getConfig().getString("messages.reload-success")));
            }
            case "list" -> {
                sender.sendMessage(mm(prefix() + "<gray>Modules enregistrés:"));
                for (Module m : plugin.getModuleManager().getRegistered().values()) {
                    boolean enabled = plugin.getModuleManager().isEnabled(m.getId());
                    String status = enabled ? "<green>ACTIF" : "<red>INACTIF";
                    sender.sendMessage(mm("  <dark_gray>- <white>" + m.getDisplayName() + " <gray>(" + m.getId() + ") " + status));
                }
            }
            default -> sender.sendMessage(mm(prefix() + "<gray>Usage: /mainadmin <reload|list>"));
        }
        return true;
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "<dark_gray>[<aqua>Main<dark_gray>] <gray>");
    }

    // Petit helper pour convertir un message MiniMessage en composant Adventure
    private net.kyori.adventure.text.Component mm(String message) {
        return MiniMessage.miniMessage().deserialize(message);
    }
}
