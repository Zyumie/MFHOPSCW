package fr.zyumie.storm;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.storm.command.StormCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Storm extends JavaPlugin implements Module {

    private StormManager  stormManager;
    private StormScheduler stormScheduler;

    private File customDataFolder;
    private File configFile;
    private FileConfiguration config;

    // ── Dossier personnalisé : plugins/Main/Storm ─────────────────────

    public File getCustomDataFolder() {
        if (customDataFolder == null) {
            File mainFolder = new File(getServer().getPluginsFolder(), "Main");
            customDataFolder = new File(mainFolder, "Storm");
            if (!customDataFolder.exists()) {
                customDataFolder.mkdirs();
            }
        }
        return customDataFolder;
    }

    // ── Gestion personnalisée du config.yml ───────────────────────────

    @Override
    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(getCustomDataFolder(), "config.yml");
        }
        if (!configFile.exists()) {
            saveResource("config.yml", false);
            // Déplace le config.yml généré par défaut vers plugins/Main/Storm s'il est au mauvais endroit
            File defaultFile = new File(super.getDataFolder(), "config.yml");
            if (defaultFile.exists()) {
                defaultFile.renameTo(configFile);
            }
        }
    }

    @Override
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    @Override
    public void reloadConfig() {
        if (configFile == null) {
            configFile = new File(getCustomDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = getResource("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }
    }

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
        stormScheduler.init();

        StormCommand cmd = new StormCommand(this);
        getCommand("storm").setExecutor(cmd);
        getCommand("storm").setTabCompleter(cmd);

        getLogger().info("StormModule activé — Fichiers sauvegardés dans plugins/Main/Storm");
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