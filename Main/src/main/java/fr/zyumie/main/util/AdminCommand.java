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
            sender.sendMessage(legacyToComponent(prefix() + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(legacyToComponent(prefix() + "&7Usage: /mainadmin <reload|list>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getModuleManager().reloadAll();
                // reloadAll() réactive tout selon config.yml sans savoir qu'une tempête
                // est en cours : on réapplique le verrou storm-lockout immédiatement après.
                plugin.getStormLockoutManager().reapplyIfStormActive();
                sender.sendMessage(legacyToComponent(prefix() + plugin.getConfig().getString("messages.reload-success")));
            }
            case "list" -> {
                sender.sendMessage(legacyToComponent(prefix() + "&7Modules enregistrés:"));
                for (Module m : plugin.getModuleManager().getRegistered().values()) {
                    boolean enabled = plugin.getModuleManager().isEnabled(m.getId());
                    String status = enabled ? "&aACTIF" : "&cINACTIF";
                    sender.sendMessage(legacyToComponent("  &8- &f" + m.getDisplayName() + " &7(" + m.getId() + ") " + status));
                }
            }
            default -> sender.sendMessage(legacyToComponent(prefix() + "&7Usage: /mainadmin <reload|list>"));
        }
        return true;
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "&8[&bMain&8] &7");
    }

    // Petit helper pour convertir le code couleur '&' en composant Adventure via MiniMessage
    private net.kyori.adventure.text.Component legacyToComponent(String legacy) {
        String miniMessage = legacy
                .replace("&8", "<dark_gray>")
                .replace("&b", "<aqua>")
                .replace("&7", "<gray>")
                .replace("&a", "<green>")
                .replace("&c", "<red>")
                .replace("&f", "<white>");
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }
}
