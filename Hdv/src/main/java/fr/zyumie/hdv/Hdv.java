package fr.zyumie.hdv;

import fr.zyumie.hdv.command.HdvCommand;
import fr.zyumie.hdv.gui.HdvGui;
import fr.zyumie.hdv.gui.HdvHolder;
import fr.zyumie.hdv.listener.HdvGuiListener;
import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Hdv extends JavaPlugin implements Module {

    private HdvManager hdvManager;
    private HdvGui hdvGui;
    private FileConfiguration guiConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("Gui.yml", false);

        this.guiConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "Gui.yml"));

        hdvManager = new HdvManager(this);
        MainAPI.get().getModuleManager().register(this);
    }

    @Override
    public void onDisable() {
        if (hdvManager != null) hdvManager.saveAll();
    }

    @Override public String getId()          { return "hdv"; }
    @Override public String getDisplayName() { return "Hôtel des Ventes"; }

    @Override
    public void onModuleEnable() {
        // CORRECTION : Ajout de guiConfig en troisième argument
        hdvGui = new HdvGui(this, hdvManager, guiConfig);
        hdvManager.init();

        getServer().getPluginManager().registerEvents(
                new HdvGuiListener(this, hdvManager, hdvGui), this);

        HdvCommand cmd = new HdvCommand(this, hdvManager, hdvGui);
        getCommand("hdv").setExecutor(cmd);
        getCommand("hdv").setTabCompleter(cmd);
        getLogger().info("HdvModule activé.");
    }

    @Override
    public void onModuleDisable() {
        hdvManager.saveAll();
        getLogger().info("HdvModule désactivé.");
    }

    public void reload() {
        reloadConfig();
        // Recharger aussi le fichier Gui.yml en cas de reload
        this.guiConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "Gui.yml"));

        // CORRECTION : Ajout de guiConfig ici aussi
        hdvGui = new HdvGui(this, hdvManager, guiConfig);

        getServer().getOnlinePlayers().forEach(p -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof HdvHolder)
                p.closeInventory();
        });
    }

    public HdvManager getHdvManager() { return hdvManager; }
}