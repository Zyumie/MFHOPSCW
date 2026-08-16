package fr.zyumie.main.oxygen;

import fr.zyumie.main.module.Module;
import fr.zyumie.main.oxygen.api.OxygenSupplier;
import fr.zyumie.main.oxygen.command.OxygenCommand;
import fr.zyumie.main.oxygen.listener.PlayerConnectionListener;
import fr.zyumie.main.oxygen.listener.PlayerZoneListener;
import fr.zyumie.main.oxygen.task.OxygenTickTask;
import org.bukkit.event.HandlerList;

/**
 * Module Oxygène enregistré auprès du MainPlugin via ModuleManager.
 * C'est lui qui démarre/stoppe la logique réelle (task, listeners, commande).
 */
public class OxygenModule implements Module {

    private final Oxygen plugin;
    private OxygenTickTask tickTask;

    public OxygenModule(Oxygen plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "oxygen";
    }

    @Override
    public String getDisplayName() {
        return "Oxygène";
    }

    @Override
    public void onModuleEnable() {
        OxygenManager manager = plugin.getOxygenManager();

        // Listeners
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(plugin, manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerZoneListener(plugin, manager), plugin);

        // Task périodique
        int intervalTicks = plugin.getConfig().getInt("oxygen.check-interval-ticks", 20);
        tickTask = new OxygenTickTask(plugin, manager);
        tickTask.runTaskTimer(plugin, intervalTicks, intervalTicks);

        // Commande
        var cmd = plugin.getCommand("oxygen");
        if (cmd != null) {
            OxygenCommand oxyCmd = new OxygenCommand(plugin, manager);
            cmd.setExecutor(oxyCmd);
            cmd.setTabCompleter(oxyCmd);
        }

        // Charge les joueurs déjà connectés (si /reload en cours de partie)
        plugin.getServer().getOnlinePlayers().forEach(manager::loadPlayer);

        plugin.getLogger().info("Module Oxygène actif — seuil surface Y=" +
                plugin.getConfig().getInt("oxygen.surface-y-threshold", 60));
    }

    @Override
    public void onModuleDisable() {
        HandlerList.unregisterAll(plugin);
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        plugin.getOxygenManager().saveAll();
        plugin.getLogger().info("Module Oxygène désactivé, données sauvegardées.");
    }

    /**
     * Appelé par OxygenArmorPlugin (ou tout autre plugin) pour enregistrer une source
     * externe d'oxygène (ex: la réserve de l'armure).
     */
    public void registerSupplier(OxygenSupplier supplier) {
        plugin.getOxygenManager().registerSupplier(supplier);
    }

    public void unregisterSupplier(OxygenSupplier supplier) {
        plugin.getOxygenManager().unregisterSupplier(supplier);
    }

    public OxygenManager getManager() {
        return plugin.getOxygenManager();
    }
}
