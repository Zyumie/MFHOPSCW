package fr.zyumie.main;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.integration.MisaneBridge;
import fr.zyumie.main.module.ModuleManager;
import fr.zyumie.main.storage.StorageManager;
import fr.zyumie.main.util.AdminCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private ModuleManager moduleManager;
    private StorageManager storageManager;
    private MisaneBridge misaneBridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.moduleManager = new ModuleManager(this);
        this.storageManager = new StorageManager(this);
        this.misaneBridge = new MisaneBridge(this);

        // MainAPI doit être initialisée avant que les sous-plugins ne se chargent.
        // Comme ces sous-plugins déclarent "depend: [MainPlugin]", Bukkit garantit
        // que ce onEnable() tourne entièrement avant le leur.
        MainAPI.init(this);

        misaneBridge.setup();

        getCommand("mainadmin").setExecutor(new AdminCommand(this));

        getLogger().info("=================================");
        getLogger().info(" MainPlugin activé.");
        getLogger().info(" Stockage: " + getConfig().getString("storage.type"));
        getLogger().info(" En attente d'enregistrement des modules...");
        getLogger().info("=================================");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (storageManager != null) {
            storageManager.close();
        }
        getLogger().info("MainPlugin désactivé.");
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public MisaneBridge getMisaneBridge() {
        return misaneBridge;
    }
}
