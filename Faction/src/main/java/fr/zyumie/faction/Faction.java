package fr.zyumie.faction;

import fr.zyumie.faction.api.FactionAPI;
import fr.zyumie.faction.command.FactionCommand;
import fr.zyumie.faction.listener.FactionManager;
import fr.zyumie.faction.listener.FactionPvpListener;
import fr.zyumie.faction.listener.FactionTeleportListener;
import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import org.bukkit.plugin.java.JavaPlugin;

public class Faction extends JavaPlugin implements Module {

    private FactionManager factionManager;
    private GradeHelper gradeHelper;

    @Override
    public void onEnable() {

        this.factionManager = new FactionManager(this);
        this.factionManager.loadAll();

        saveDefaultConfig();
        factionManager = new FactionManager(this);
        gradeHelper    = new GradeHelper(this);
        MainAPI.get().getModuleManager().register(this);
    }

    @Override
    public void onDisable() {
        if (factionManager != null) factionManager.saveAll();
    }

    @Override public String getId()          { return "faction"; }
    @Override public String getDisplayName() { return "Factions"; }

    @Override
    public void onModuleEnable() {
        factionManager.loadAll();
        FactionAPI.init(factionManager);

        // Economy lookup est lazy dans FactionCommand — pas besoin de Vault ici
        FactionCommand cmd = new FactionCommand(this, factionManager, gradeHelper);
        getCommand("f").setExecutor(cmd);
        getCommand("f").setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new FactionPvpListener(factionManager), this);
        getServer().getPluginManager().registerEvents(new FactionTeleportListener(cmd), this);
        getLogger().info("FactionModule activé.");
    }

    @Override
    public void onModuleDisable() {
        factionManager.saveAll();
        getLogger().info("FactionModule désactivé.");
    }

    public FactionManager getFactionManager() { return factionManager; }
    public GradeHelper getGradeHelper()       { return gradeHelper; }
    public FactionAPI getFactionAPI()         { return FactionAPI.get(); }
}
