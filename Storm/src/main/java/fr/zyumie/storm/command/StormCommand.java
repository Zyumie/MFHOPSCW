package fr.zyumie.storm.command;

import fr.zyumie.storm.Storm;
import fr.zyumie.storm.data.StormState;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class StormCommand implements CommandExecutor, TabCompleter {

    private final Storm plugin;

    public StormCommand(Storm plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("storm.admin")) {
            send(sender, plugin.getConfig().getString("messages.no-permission", "")); return true;
        }

        if (args.length == 0) { sendUsage(sender); return true; }

        switch (args[0].toLowerCase()) {

            case "start" -> {
                // /storm start [durée ex: 30m, 2h, 1d]
                String durationArg = "2h";
                if (args.length >= 2) {
                    durationArg = args[1];
                }
                if (plugin.getStormManager().isStormActive()) {
                    send(sender, "<red>Une tempête est déjà en cours."); return true;
                }
                plugin.getScheduler().forceStart(durationArg);
                plugin.getStormManager().startStorm(true);
                send(sender, plugin.getConfig().getString("messages.admin-start", ""));
            }

            case "stop" -> {
                if (!plugin.getStormManager().isStormActive()) {
                    send(sender, "<red>Aucune tempête en cours."); return true;
                }
                plugin.getScheduler().forceStop();
                plugin.getStormManager().stopStorm(true);
                send(sender, plugin.getConfig().getString("messages.admin-stop", ""));
            }

            case "status" -> {
                StormState state = plugin.getScheduler().getCurrentState();
                if (state == null) {
                    send(sender, plugin.getConfig().getString("messages.no-storm", "")); return true;
                }
                if (plugin.getStormManager().isStormActive()) {
                    send(sender, plugin.getConfig().getString("messages.storm-active", "")
                            .replace("{end-date}", state.getEndDateFormatted()));
                } else {
                    send(sender, plugin.getConfig().getString("messages.scheduled", "")
                            .replace("{start-date}", state.getStartDateFormatted())
                            .replace("{duration}", state.getFormattedDuration()));
                }
            }

            case "schedule" -> {
                plugin.getScheduler().init();
                StormState state = plugin.getScheduler().getCurrentState();
                if (state != null) {
                    send(sender, plugin.getConfig().getString("messages.scheduled", "")
                            .replace("{start-date}", state.getStartDateFormatted())
                            .replace("{duration}", state.getFormattedDuration()));
                }
            }

            case "reload" -> {
                plugin.reloadConfig();
                send(sender, plugin.getConfig().getString("messages.admin-reload", ""));
            }

            default -> sendUsage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("storm.admin")) return List.of();
        if (args.length == 1) return List.of("start", "stop", "status", "schedule", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("start"))
            return List.of("30m", "1h", "2h", "6h", "12h", "1d", "2d");
        return List.of();
    }

    private void sendUsage(CommandSender s) {
        send(s, "<gold>/storm <gray>start [durée ex: 30m/2h/1d] | stop | status | schedule | reload");
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "<dark_gray>[<blue>⛈<dark_gray>] <gray>");
    }

    private void send(CommandSender s, String message) {
        s.sendMessage(MiniMessage.miniMessage().deserialize(prefix() + message));
    }
}