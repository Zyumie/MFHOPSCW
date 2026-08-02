package fr.zyumie.home.command;

import fr.zyumie.home.HomeManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomeCommands implements CommandExecutor {

    private final fr.zyumie.home.Home plugin;
    private final HomeManager manager;

    public HomeCommands(fr.zyumie.home.Home plugin, HomeManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }

        switch (cmd.getName().toLowerCase()) {
            case "sethome" -> handleSetHome(player);
            case "home"    -> handleHome(player);
            case "delhome" -> handleDelHome(player);
        }
        return true;
    }

    private void handleSetHome(Player player) {
        if (!manager.hasDiedOnce(player.getUniqueId())) {
            send(player, msg("messages.set-requires-death"));
            return;
        }
        manager.setHome(player.getUniqueId(), player.getLocation());
        manager.savePlayer(player.getUniqueId());
        send(player, msg("messages.set-success"));
    }

    private void handleHome(Player player) {
        if (!manager.hasHome(player.getUniqueId())) {
            send(player, msg("messages.tp-none"));
            return;
        }
        long remaining = manager.getRemainingCooldownSeconds(player.getUniqueId());
        if (remaining > 0) {
            send(player, msg("messages.tp-cooldown").replace("{remaining}", String.valueOf(remaining)));
            return;
        }
        player.teleport(manager.getHome(player.getUniqueId()));
        send(player, msg("messages.tp-success"));
    }

    private void handleDelHome(Player player) {
        if (!manager.hasHome(player.getUniqueId())) {
            send(player, msg("messages.del-none"));
            return;
        }
        manager.deleteHome(player.getUniqueId());
        manager.savePlayer(player.getUniqueId());
        send(player, msg("messages.del-success"));
    }

    private String msg(String path) {
        return plugin.getConfig().getString(path, "");
    }

    private void send(Player player, String legacy) {
        String prefix = msg("messages.prefix");
        String full = prefix + legacy;
        String mm = full.replace("&8","<dark_gray>").replace("&a","<green>")
                .replace("&c","<red>").replace("&e","<yellow>")
                .replace("&7","<gray>").replace("&b","<aqua>");
        player.sendMessage(MiniMessage.miniMessage().deserialize(mm));
    }
}
