package fr.zyumie.quest;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.quest.command.QuestCommand;
import fr.zyumie.quest.listener.QuestListener;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class WeeklyQuest extends JavaPlugin implements Module {

    private QuestManager questManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        questManager = new QuestManager(this);
        getServer().getScheduler().runTaskLater(this, () ->
            MainAPI.get().getModuleManager().register(this), 2L);
    }

    @Override
    public void onDisable() {
        if (questManager != null) questManager.saveAll();
    }

    @Override public String getId()          { return "weekly-quest"; }
    @Override public String getDisplayName() { return "Quêtes Hebdomadaires"; }
    @Override public String[] getDependencies() { return new String[]{"faction"}; }

    @Override
    public void onModuleEnable() {
        questManager.loadDefinitions();

        Economy economy = null;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) economy = rsp.getProvider();

        getServer().getPluginManager().registerEvents(
                new QuestListener(this, questManager, economy), this);

        QuestCommand questCmd = new QuestCommand(this, questManager);
        getCommand("quests").setExecutor(questCmd);
        getCommand("quests").setTabCompleter(questCmd);

        getLogger().info("WeeklyQuestModule activé — Semaine " + questManager.getCurrentWeek()
                + " sur " + getConfig().getInt("quest.cycle-weeks", 12) + ".");
    }

    @Override
    public void onModuleDisable() {
        HandlerList.unregisterAll(this);
        questManager.saveAll();
        getLogger().info("WeeklyQuestModule désactivé.");
    }

    public QuestManager getQuestManager() { return questManager; }
}
