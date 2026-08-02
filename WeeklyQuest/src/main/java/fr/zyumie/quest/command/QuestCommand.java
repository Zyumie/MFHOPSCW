package fr.zyumie.quest.command;

import fr.zyumie.quest.QuestManager;
import fr.zyumie.quest.WeeklyQuest;
import fr.zyumie.quest.model.FactionQuestProgress;
import fr.zyumie.quest.model.QuestDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class QuestCommand implements CommandExecutor, TabCompleter {

    private final WeeklyQuest plugin;
    private final QuestManager questManager;

    public QuestCommand(WeeklyQuest plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Commande réservée aux joueurs.");
            return true;
        }

        String factionName;
        if (args.length >= 1) {
            factionName = args[0];
        } else {
            factionName = getFactionOf(player);
            if (factionName == null) {
                player.sendMessage(mm("<red>Tu n'es dans aucune faction. Usage: /quests <faction>"));
                return true;
            }
        }

        int week = questManager.getCurrentWeek();
        List<QuestDefinition> quests = questManager.getCurrentWeekQuests();

        player.sendMessage(mm("<dark_gray>┌─── <gold><bold>Quêtes Semaine " + week + "</bold> <dark_gray>── <yellow>" + factionName + " <dark_gray>───"));

        if (quests.isEmpty()) {
            player.sendMessage(mm("<dark_gray>│ <gray>Aucune quête configurée pour cette semaine."));
        } else {
            for (QuestDefinition q : quests) {
                FactionQuestProgress prog = questManager.getProgress(factionName, q.getId());
                int current = prog.getProgress();
                int max = q.getAmount();
                boolean done = prog.isCompleted();

                // Barre de progression visuelle [████░░░░]
                int barLength = 8;
                int filled = done ? barLength : (int) Math.min(barLength, (current * (double) barLength / max));
                String bar = "<green>" + "█".repeat(filled) + "<dark_gray>" + "░".repeat(barLength - filled);

                String status = done ? "<green>✔ COMPLÉTÉE" : "<yellow>" + current + "/" + max;
                player.sendMessage(mm("<dark_gray>│"));
                player.sendMessage(mm("<dark_gray>│ <white><bold>" + q.getTitle()));
                player.sendMessage(mm("<dark_gray>│ <gray>" + q.getDescription()));
                player.sendMessage(mm("<dark_gray>│ [" + bar + "<dark_gray>] " + status));
                if (!done) {
                    player.sendMessage(mm("<dark_gray>│ <gray>Récompense: <yellow>+" + String.format("%.0f", q.getRewardMoney()) + " bank"));
                }
            }
        }
        player.sendMessage(mm("<dark_gray>└────────────────────────────────"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        // Suggère les noms de factions disponibles
        if (args.length == 1) {
            var factionPlugin = Bukkit.getPluginManager().getPlugin("FactionPlugin");
            if (factionPlugin != null) {
                try {
                    Object mgr = factionPlugin.getClass().getMethod("getFactionManager").invoke(factionPlugin);
                    @SuppressWarnings("unchecked")
                    var factions = (Iterable<?>) mgr.getClass().getMethod("getAllFactions").invoke(mgr);
                    return java.util.stream.StreamSupport.stream(factions.spliterator(), false)
                            .map(f -> {
                                try { return (String) f.getClass().getMethod("getName").invoke(f); }
                                catch (Exception e) { return ""; }
                            })
                            .filter(s -> !s.isEmpty())
                            .toList();
                } catch (Exception ignored) {}
            }
        }
        return List.of();
    }

    private String getFactionOf(Player player) {
        var factionPlugin = Bukkit.getPluginManager().getPlugin("FactionPlugin");
        if (factionPlugin == null) return null;
        try {
            var api = factionPlugin.getClass().getMethod("getFactionAPI").invoke(factionPlugin);
            Object result = api.getClass().getMethod("getFactionName", UUID.class).invoke(api, player.getUniqueId());
            return result != null ? result.toString() : null;
        } catch (Exception e) { return null; }
    }

    private Component mm(String s) {
        return MiniMessage.miniMessage().deserialize(s);
    }
}
