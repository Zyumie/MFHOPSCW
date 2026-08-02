package fr.zyumie.quest.listener;

import fr.zyumie.quest.QuestManager;
import fr.zyumie.quest.WeeklyQuest;
import fr.zyumie.quest.model.QuestDefinition;
import fr.zyumie.quest.model.QuestDefinition.QuestType;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestListener implements Listener {

    private final WeeklyQuest plugin;
    private final QuestManager questManager;
    private final Economy economy;
    // Accumulation des blocs parcourus par joueur avant flush (évite le spam d'appels)
    private final Map<UUID, Double> travelBuffer = new HashMap<>();

    public QuestListener(WeeklyQuest plugin, QuestManager questManager, Economy economy) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.economy = economy;
    }

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        String faction = getFactionOf(killer);
        if (faction == null) return;

        String mobType = event.getEntityType().name();
        List<QuestDefinition> done = questManager.addProgress(faction, QuestType.KILL_MOB, mobType, 1);
        giveRewards(faction, done);
    }

    @EventHandler
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String faction = getFactionOf(player);
        if (faction == null) return;

        String blockType = event.getBlock().getType().name();
        List<QuestDefinition> done = questManager.addProgress(faction, QuestType.MINE_BLOCK, blockType, 1);
        giveRewards(faction, done);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String faction = getFactionOf(player);
        if (faction == null) return;

        String item = event.getRecipe().getResult().getType().name();
        int amount = event.getRecipe().getResult().getAmount();
        List<QuestDefinition> done = questManager.addProgress(faction, QuestType.CRAFT_ITEM, item, amount);
        giveRewards(faction, done);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        Player player = event.getPlayer();
        String faction = getFactionOf(player);
        if (faction == null) return;

        double dist = event.getFrom().distance(event.getTo());
        travelBuffer.merge(player.getUniqueId(), dist, Double::sum);

        double buffered = travelBuffer.getOrDefault(player.getUniqueId(), 0.0);
        if (buffered >= 10.0) {
            int blocks = (int) buffered;
            travelBuffer.put(player.getUniqueId(), buffered - blocks);
            List<QuestDefinition> done = questManager.addProgress(faction, QuestType.TRAVEL_DISTANCE, null, blocks);
            giveRewards(faction, done);
        }
    }

    private void giveRewards(String factionName, List<QuestDefinition> completed) {
        if (completed.isEmpty()) return;
        for (QuestDefinition q : completed) {
            if (economy != null && q.getRewardMoney() > 0) {
                // Via FactionPlugin (réflexion) → dépôt dans la bank
                depositFactionBank(factionName, q.getRewardMoney());
            }
            // Broadcast à tous les membres en ligne
            String msg = "<gold>[Quête] <green>" + q.getTitle() + " <yellow>complétée par la faction <green>"
                    + factionName + "<yellow>!";
            if (q.getRewardMessage() != null && !q.getRewardMessage().isEmpty()) {
                msg += " <gray>" + q.getRewardMessage();
            }
            final String finalMsg = msg;
            Bukkit.getOnlinePlayers().forEach(p -> {
                String pFaction = getFactionOf(p);
                if (factionName.equalsIgnoreCase(pFaction)) {
                    p.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize(finalMsg));
                }
            });
            questManager.saveAll();
        }
    }

    private String getFactionOf(Player player) {
        var factionPlugin = Bukkit.getPluginManager().getPlugin("FactionPlugin");
        if (factionPlugin == null) return null;
        try {
            var api = factionPlugin.getClass().getMethod("getFactionAPI").invoke(factionPlugin);
            Object result = api.getClass().getMethod("getFactionName", UUID.class)
                    .invoke(api, player.getUniqueId());
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void depositFactionBank(String factionName, double amount) {
        var factionPlugin = Bukkit.getPluginManager().getPlugin("FactionPlugin");
        if (factionPlugin == null) return;
        try {
            var mgr = factionPlugin.getClass().getMethod("getFactionManager").invoke(factionPlugin);
            Object faction = mgr.getClass().getMethod("getFaction", String.class).invoke(mgr, factionName);
            if (faction == null) return;
            faction.getClass().getMethod("depositBank", double.class).invoke(faction, amount);
            mgr.getClass().getMethod("saveFaction", faction.getClass()).invoke(mgr, faction);
        } catch (Exception e) {
            plugin.getLogger().warning("Impossible de déposer la récompense dans la bank: " + e.getMessage());
        }
    }
}
