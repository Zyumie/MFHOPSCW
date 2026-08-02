package fr.zyumie.main.api;

import fr.zyumie.main.Main;
import fr.zyumie.main.integration.MisaneBridge;
import fr.zyumie.main.module.ModuleManager;
import fr.zyumie.main.storage.DataStore;

/**
 * Point d'entrée unique utilisé par tous les sous-plugins (Oxygène, Faction, HDV, ...)
 * pour accéder aux services du Main.
 *
 * Exemple d'usage côté sous-plugin:
 *
 *   public class OxygenPlugin extends JavaPlugin implements Listener {
 *       @Override
 *       public void onEnable() {
 *           MainAPI.get().getModuleManager().register(new OxygenModule(this));
 *       }
 *   }
 *
 * IMPORTANT: le sous-plugin doit déclarer "depend: [MainPlugin]" dans son plugin.yml
 * pour garantir que MainAPI.get() ne soit jamais null au moment de l'appel.
 */
public final class MainAPI {

    private static MainAPI instance;

    private final Main plugin;

    private MainAPI(Main plugin) {
        this.plugin = plugin;
    }

    public static void init(Main plugin) {
        instance = new MainAPI(plugin);
    }

    public static MainAPI get() {
        if (instance == null) {
            throw new IllegalStateException(
                "MainAPI n'est pas initialisée. Vérifie que MainPlugin est bien chargé " +
                "et que ton sous-plugin a 'depend: [MainPlugin]' dans son plugin.yml."
            );
        }
        return instance;
    }

    public ModuleManager getModuleManager() {
        return plugin.getModuleManager();
    }

    public DataStore getDataStore() {
        return plugin.getStorageManager().getDataStore();
    }

    public MisaneBridge getMisaneBridge() {
        return plugin.getMisaneBridge();
    }

    public Main getPlugin() {
        return plugin;
    }
}
