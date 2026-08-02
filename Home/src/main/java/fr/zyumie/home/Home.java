package fr.zyumie.home;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.home.command.HomeCommands;
import fr.zyumie.home.listener.HomeListener;
import org.bukkit.plugin.java.JavaPlugin;

public class Home extends JavaPlugin implements Module {

    private HomeManager homeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        homeManager = new HomeManager(this);
        MainAPI.get().getModuleManager().register(this);
    }

    @Override
    public void onDisable() {
        if (homeManager != null) homeManager.saveAll();
    }

    @Override public String getId()          { return "home"; }
    @Override public String getDisplayName() { return "Home"; }

    @Override
    public void onModuleEnable() {
        getServer().getPluginManager().registerEvents(new HomeListener(homeManager), this);
        HomeCommands cmds = new HomeCommands(this, homeManager);
        getCommand("home").setExecutor(cmds);
        getCommand("sethome").setExecutor(cmds);
        getCommand("delhome").setExecutor(cmds);
        getLogger().info("HomeModule activé.");
    }

    @Override
    public void onModuleDisable() {
        homeManager.saveAll();
        getLogger().info("HomeModule désactivé.");
    }

    public HomeManager getHomeManager() { return homeManager; }
}
