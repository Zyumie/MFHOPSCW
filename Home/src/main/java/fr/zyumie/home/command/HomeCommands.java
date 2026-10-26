package fr.zyumie.home.command;

import fr.zyumie.home.Home;
import fr.zyumie.home.HomeManager;
import fr.zyumie.home.gui.HomeGui;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class HomeCommands implements CommandExecutor {

    private final Home plugin;
    private final HomeManager manager;

    public HomeCommands(Home plugin, HomeManager manager) {
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
            case "sethome" -> handleSetHome(player, args);
            case "home"    -> handleHome(player, args);
            case "delhome" -> handleDelHome(player, args);
        }
        return true;
    }

    private void handleSetHome(Player player, String[] args) {
        String homeName = args.length > 0 ? args[0].toLowerCase() : "default";
        boolean hasExisting = manager.hasHome(player.getUniqueId(), homeName);
        int currentCount = manager.getHomeCount(player.getUniqueId());
        int maxHomes = manager.getMaxHomes(player);

        if (!hasExisting && currentCount >= maxHomes) {
            send(player, msg("messages.max-limit").replace("{max}", String.valueOf(maxHomes)));
            return;
        }

        manager.setHome(player.getUniqueId(), homeName, player.getLocation());
        manager.savePlayer(player.getUniqueId());
        send(player, msg("messages.set-success").replace("{home}", homeName));
    }

    private void handleHome(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            if (manager.getHomeCount(player.getUniqueId()) == 0) {
                send(player, msg("messages.tp-none"));
                return;
            }
            new HomeGui(plugin, player).open();
            return;
        }

        String homeName = args[0].toLowerCase();
        if (!manager.hasHome(player.getUniqueId(), homeName)) {
            send(player, msg("messages.tp-none-named").replace("{home}", homeName));
            return;
        }

        initiateTeleport(plugin, manager, player, homeName);
    }

    public static void initiateTeleport(Home plugin, HomeManager manager, Player player, String homeName) {
        Location targetLoc = manager.getHome(player.getUniqueId(), homeName);
        if (targetLoc == null) return;

        int delaySeconds = plugin.getConfig().getInt("home.teleport-delay-seconds", 20);

        if (delaySeconds <= 0) {
            player.teleport(targetLoc);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            player.sendMessage(MiniMessage.miniMessage().deserialize(plugin.getConfig().getString("messages.prefix", "") + plugin.getConfig().getString("messages.tp-success", "")));
            return;
        }

        sendRaw(player, plugin, plugin.getConfig().getString("messages.tp-delay-start", "<green>Téléportation dans <yellow>{seconds}s<green>. Ne bouge pas !").replace("{seconds}", String.valueOf(delaySeconds)));

        Location initialLoc = player.getLocation();

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            manager.cancelTeleport(player.getUniqueId());
            if (player.isOnline()) {
                player.teleport(targetLoc);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                sendRaw(player, plugin, plugin.getConfig().getString("messages.tp-success", "<green>Téléportation effectuée !"));
            }
        }, delaySeconds * 20L); // 20 ticks = 1 seconde

        manager.setTeleportTask(player.getUniqueId(), task, initialLoc);
    }

    private void handleDelHome(Player player, String[] args) {
        String homeName = args.length > 0 ? args[0].toLowerCase() : "default";

        if (!manager.hasHome(player.getUniqueId(), homeName)) {
            send(player, msg("messages.del-none"));
            return;
        }

        manager.deleteHome(player.getUniqueId(), homeName);
        manager.savePlayer(player.getUniqueId());
        send(player, msg("messages.del-success").replace("{home}", homeName));
    }

    private String msg(String path) {
        return plugin.getConfig().getString(path, "");
    }

    private void send(Player player, String message) {
        sendRaw(player, plugin, message);
    }

    public static void sendRaw(Player player, Home plugin, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        player.sendMessage(MiniMessage.miniMessage().deserialize(prefix + message));
    }
}