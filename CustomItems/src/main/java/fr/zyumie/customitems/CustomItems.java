package fr.zyumie.customitems;

import fr.zyumie.customitems.command.GiveItemCommand;
import fr.zyumie.customitems.handler.armor.*;
import fr.zyumie.customitems.handler.weapon.*;
import fr.zyumie.customitems.listener.*;
import fr.zyumie.main.api.MainAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import fr.zyumie.main.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class CustomItems extends JavaPlugin implements Module {

    private StormArmorHandler  stormHandler;
    private GhostArmorHandler  ghostHandler;
    private RescueArmorHandler rescueHandler;
    private SoulArmorHandler   soulHandler;

    private CelestialSwordHandler celestialHandler;
    private ExplosiveBowHandler   explosiveBowHandler;
    private EnderBowHandler       enderBowHandler;
    private BerserkSwordHandler   berserkHandler;

    private ItemRegistry  itemRegistry;
    private ArmorListener armorListener;

    // Fichiers de configuration dans plugins/Main/CustomItems
    private FileConfiguration mainConfig;
    private FileConfiguration customArmorConfig;
    private FileConfiguration weaponConfig;

    @Override
    public void onEnable() {
        createCustomConfigs(); // Charge les fichiers dans plugins/Main/CustomItems

        initHandlers();
        itemRegistry = new ItemRegistry(this);

        getServer().getScheduler().runTaskLater(this,
                () -> MainAPI.get().getModuleManager().register(this), 1L);
    }

    @Override
    public void onDisable() {
        if (armorListener != null) armorListener.stopTask();
        Bukkit.getOnlinePlayers().forEach(p -> {
            ghostHandler.cleanup(p);
            berserkHandler.cleanup(p);
            celestialHandler.removeFromFlight(p);
        });
    }

    @Override public String getId()          { return "custom-items"; }
    @Override public String getDisplayName() { return "Items & Armures Custom"; }

    @Override
    public void onModuleEnable() {
        armorListener = new ArmorListener(this);
        getServer().getPluginManager().registerEvents(armorListener,              this);
        getServer().getPluginManager().registerEvents(new DamageListener(this),   this);
        getServer().getPluginManager().registerEvents(new KillListener(this),     this);
        getServer().getPluginManager().registerEvents(new ProjectileListener(this),this);
        getServer().getPluginManager().registerEvents(new InteractListener(this),  this);
        getServer().getPluginManager().registerEvents(celestialHandler, this);
        getServer().getPluginManager().registerEvents(new ItemProtectionListener(this), this);

        armorListener.startTask();

        GiveItemCommand giveCmd = new GiveItemCommand(this);
        getCommand("items").setExecutor(giveCmd);
        getCommand("items").setTabCompleter(giveCmd);

        hookStorm();

        getLogger().info("CustomItemsModule activé (Dossier : plugins/Main/CustomItems).");
    }

    @Override
    public void onModuleDisable() {
        HandlerList.unregisterAll(this);
        if (armorListener != null) armorListener.stopTask();
        getLogger().info("CustomItemsModule désactivé.");
    }

    // ── Gestion du dossier personnalisé plugins/Main/CustomItems ───────────

    public File getCustomDataFolder() {
        // Remonte d'un niveau depuis plugins/CustomItems pour aller dans plugins/Main/CustomItems
        return new File(getDataFolder().getParentFile(), "Main/CustomItems");
    }

    private void createCustomConfigs() {
        File customFolder = getCustomDataFolder();
        if (!customFolder.exists()) {
            customFolder.mkdirs();
        }

        // 1. config.yml général
        File configFile = new File(customFolder, "config.yml");
        if (!configFile.exists()) {
            saveResourceToFile("config.yml", configFile);
        }
        mainConfig = YamlConfiguration.loadConfiguration(configFile);

        // 2. CustomArmor.yml
        File customArmorFile = new File(customFolder, "CustomArmor.yml");
        if (!customArmorFile.exists()) {
            saveResourceToFile("CustomArmor.yml", customArmorFile);
        }
        customArmorConfig = YamlConfiguration.loadConfiguration(customArmorFile);

        // 3. Weapon.yml
        File weaponFile = new File(customFolder, "Weapon.yml");
        if (!weaponFile.exists()) {
            saveResourceToFile("Weapon.yml", weaponFile);
        }
        weaponConfig = YamlConfiguration.loadConfiguration(weaponFile);
    }

    private void saveResourceToFile(String resourcePath, File targetFile) {
        try (var in = getResource(resourcePath)) {
            if (in != null) {
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            getLogger().warning("Impossible de copier la ressource " + resourcePath + " : " + e.getMessage());
        }
    }

    public void reloadCustomConfigs() {
        File customFolder = getCustomDataFolder();

        File configFile = new File(customFolder, "config.yml");
        if (configFile.exists()) {
            mainConfig = YamlConfiguration.loadConfiguration(configFile);
        }

        File customArmorFile = new File(customFolder, "CustomArmor.yml");
        if (customArmorFile.exists()) {
            customArmorConfig = YamlConfiguration.loadConfiguration(customArmorFile);
        }

        File weaponFile = new File(customFolder, "Weapon.yml");
        if (weaponFile.exists()) {
            weaponConfig = YamlConfiguration.loadConfiguration(weaponFile);
        }
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getCustomArmorConfig() {
        return customArmorConfig;
    }

    public FileConfiguration getWeaponConfig() {
        return weaponConfig;
    }

    // ── Hook Storm ────────────────────────────────────────────────────────

    private void hookStorm() {
        var stormPlugin = Bukkit.getPluginManager().getPlugin("Storm");
        if (stormPlugin == null) return;
        try {
            Object manager = stormPlugin.getClass()
                    .getMethod("getStormManager").invoke(stormPlugin);
            java.util.function.Predicate<org.bukkit.entity.Player> checker =
                    p -> stormHandler.isImmune(p);
            manager.getClass()
                    .getMethod("setImmunityChecker", java.util.function.Predicate.class)
                    .invoke(manager, checker);
            getLogger().info("Intégration Storm ✔");
        } catch (Exception e) {
            getLogger().warning("Hook Storm échoué : " + e.getMessage());
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────

    private void initHandlers() {
        stormHandler        = new StormArmorHandler(this);
        ghostHandler        = new GhostArmorHandler(this);
        rescueHandler       = new RescueArmorHandler(this);
        soulHandler         = new SoulArmorHandler(this);
        celestialHandler    = new CelestialSwordHandler(this);
        explosiveBowHandler = new ExplosiveBowHandler(this);
        enderBowHandler     = new EnderBowHandler(this);
        berserkHandler      = new BerserkSwordHandler(this);
    }

    // ── Accesseurs ────────────────────────────────________________________

    public StormArmorHandler     getStormHandler()       { return stormHandler; }
    public GhostArmorHandler     getGhostHandler()       { return ghostHandler; }
    public RescueArmorHandler    getRescueHandler()      { return rescueHandler; }
    public SoulArmorHandler      getSoulHandler()        { return soulHandler; }
    public CelestialSwordHandler getCelestialHandler()   { return celestialHandler; }
    public ExplosiveBowHandler   getExplosiveBowHandler(){ return explosiveBowHandler; }
    public EnderBowHandler       getEnderBowHandler()    { return enderBowHandler; }
    public BerserkSwordHandler   getBerserkHandler()     { return berserkHandler; }
    public ItemRegistry          getItemRegistry()       { return itemRegistry; }
}