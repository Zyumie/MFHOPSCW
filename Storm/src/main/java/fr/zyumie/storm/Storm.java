package fr.zyumie.storm;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.storm.command.StormCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class Storm extends JavaPlugin implements Module {

    private StormManager  stormManager;
    private StormScheduler stormScheduler;

    // ── JavaPlugin ────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stormManager   = new StormManager(this);
        stormScheduler = new StormScheduler(this);
        // Délai 1 tick pour garantir que MainAPI est initialisé
        getServer().getScheduler().runTaskLater(this,
                () -> MainAPI.get().getModuleManager().register(this), 1L);
    }

    @Override
    public void onDisable() {
        if (stormManager != null) stormManager.cleanup();
    }

    // ── Module ────────────────────────────────────────────────────────

    @Override public String getId()          { return "storm"; }
    @Override public String getDisplayName() { return "Tempête Apocalyptique"; }

    @Override
    public void onModuleEnable() {
        // Charge l'état persisté et démarre le scheduler hebdomadaire
        stormScheduler.init();

        // Commandes
        StormCommand cmd = new StormCommand(this);
        getCommand("storm").setExecutor(cmd);
        getCommand("storm").setTabCompleter(cmd);

        getLogger().info("StormModule activé — Cycle hebdomadaire en place.");
    }

    @Override
    public void onModuleDisable() {
        stormManager.cleanup();
        getLogger().info("StormModule désactivé.");
    }

    // ── Accesseurs ────────────────────────────────────────────────────

    public StormManager   getStormManager()  { return stormManager; }
    public StormScheduler getScheduler()     { return stormScheduler; }
}
