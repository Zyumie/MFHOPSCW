package fr.zyumie.shopadmin;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.shopadmin.command.ShopCommand;
import fr.zyumie.shopadmin.gui.ShopGui;
import fr.zyumie.shopadmin.listener.GuiListener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ShopAdmin extends JavaPlugin implements Module {

    private ShopManager shopManager;
    private ShopGui shopGui;
    private GuiListener guiListener;
    private FileConfiguration guiConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("Shop.yml", false);
        saveResource("Gui.yml", false);

        loadGuiConfig();

        shopManager = new ShopManager(this);
        MainAPI.get().getModuleManager().register(this);
    }

    // Méthode pour charger/recharger Gui.yml
    public void loadGuiConfig() {
        File guiFile = new File(getDataFolder(), "Gui.yml");
        if (!guiFile.exists()) {
            saveResource("Gui.yml", false);
        }
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
    }

    // Permet aux autres classes d'accéder au config
    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    @Override public void onDisable() {}
    @Override public String getId()          { return "shop-admin"; }
    @Override public String getDisplayName() { return "Shop Admin"; }

    @Override
    public void onModuleEnable() {
        shopGui     = new ShopGui(this, shopManager);
        guiListener = new GuiListener(this, shopManager, shopGui);

        shopManager.loadAll();
        getServer().getPluginManager().registerEvents(guiListener, this);

        ShopCommand cmd = new ShopCommand(this, shopManager, shopGui);
        getCommand("shop").setExecutor(cmd);
        getCommand("shop").setTabCompleter(cmd);
        getLogger().info("ShopAdmin activé.");
    }

    @Override
    public void onModuleDisable() {
        HandlerList.unregisterAll(this);
        getLogger().info("ShopAdmin désactivé.");
    }

    public void reload() {
        reloadConfig();
        loadGuiConfig();
        shopGui = new ShopGui(this, shopManager);
        shopManager.loadShop();
        getServer().getOnlinePlayers().forEach(p -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof fr.zyumie.shopadmin.gui.ShopHolder)
                p.closeInventory();
        });
    }
}
