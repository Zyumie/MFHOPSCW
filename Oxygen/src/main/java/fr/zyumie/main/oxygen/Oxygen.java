package fr.zyumie.main.oxygen;

import fr.zyumie.main.api.MainAPI;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point du plugin Oxygène.
 *
 * Ce onEnable() reste MINIMAL par design: il crée le manager et enregistre le module
 * auprès du MainPlugin. C'est le MainPlugin (via ModuleManager) qui décidera si ce module
 * doit réellement démarrer (selon modules.oxygen dans sa config.yml).
 *
 * La vraie logique de démarrage est dans {@link OxygenModule#onModuleEnable()}.
 */
public class Oxygen extends JavaPlugin {

    private OxygenManager oxygenManager;
    private OxygenModule oxygenModule;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.oxygenManager = new OxygenManager(this);
        this.oxygenModule = new OxygenModule(this);

        // API statique — doit être init avant que OxygenArmorPlugin (ou autre) ne s'enregistre
        fr.zyumie.main.oxygen.api.OxygenAPI.init(this);

        // Enregistrement auprès du Main — c'est lui qui appelle onModuleEnable() si autorisé
        MainAPI.get().getModuleManager().register(oxygenModule);
    }

    @Override
    public void onDisable() {
        // onModuleDisable() est appelé par le ModuleManager depuis MainPlugin#onDisable(),
        // mais si MainPlugin s'est arrêté avant nous (ordre inverse), on sauvegarde quand même.
        if (oxygenManager != null) {
            oxygenManager.saveAll();
        }
    }

    public OxygenManager getOxygenManager() {
        return oxygenManager;
    }

    public OxygenModule getOxygenModule() {
        return oxygenModule;
    }

    /**
     * Accès statique pratique pour les autres plugins (ex: OxygenArmorPlugin).
     *
     * Usage:
     *   OxygenPlugin.instance().getOxygenModule().registerSupplier(myArmorSupplier);
     */
    public static Oxygen instance() {
        return (Oxygen) getPlugin(Oxygen.class);
    }
}
