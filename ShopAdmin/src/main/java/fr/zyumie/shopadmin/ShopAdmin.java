package fr.zyumie.shopadmin;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.shopadmin.command.ShopCommand;
import fr.zyumie.shopadmin.gui.ShopGui;
import fr.zyumie.shopadmin.listener.GuiListener;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopAdmin extends JavaPlugin implements Module {

    private ShopManager shopManager;
    private ShopGui shopGui;
    private GuiListener guiListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("Shop.yml", false);
        shopManager = new ShopManager(this);
        MainAPI.get().getModuleManager().register(this);
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
        getLogger().info("ShopAdmin désactivé.");
    }

    public void reload() {
        reloadConfig();
        shopGui = new ShopGui(this, shopManager);
        shopManager.loadShop();
        getServer().getOnlinePlayers().forEach(p -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof fr.zyumie.shopadmin.gui.ShopHolder)
                p.closeInventory();
        });
    }
}
