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

    // Fichiers de configuration séparés
    private FileConfiguration customArmorConfig;
    private FileConfiguration weaponConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createCustomConfigs(); // Charge CustomArmor.yml et Weapon.yml

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
        // ── Listeners ───────────────────────────────────────────────────
        armorListener = new ArmorListener(this);
        getServer().getPluginManager().registerEvents(armorListener,              this);
        getServer().getPluginManager().registerEvents(new DamageListener(this),   this);
        getServer().getPluginManager().registerEvents(new KillListener(this),     this);
        getServer().getPluginManager().registerEvents(new ProjectileListener(this),this);
        getServer().getPluginManager().registerEvents(new InteractListener(this),  this);

        // CelestialSwordHandler est aussi un Listener (PlayerDropItemEvent)
        getServer().getPluginManager().registerEvents(celestialHandler, this);

        // Bloque toute modification des items custom (enclume, forge, meule, etc.)
        getServer().getPluginManager().registerEvents(new ItemProtectionListener(this), this);

        // ── Tâche armures ────────────────────────────────────────────────
        armorListener.startTask();

        // ── Commandes ────────────────────────────────────────────────────
        GiveItemCommand giveCmd = new GiveItemCommand(this);
        getCommand("items").setExecutor(giveCmd);
        getCommand("items").setTabCompleter(giveCmd);

        // ── Hook Storm ───────────────────────────────────────────────────
        hookStorm();

        getLogger().info("CustomItemsModule activé.");
    }

    @Override
    public void onModuleDisable() {
        HandlerList.unregisterAll(this);
        if (armorListener != null) armorListener.stopTask();
        getLogger().info("CustomItemsModule désactivé.");
    }

    // ── Gestion des fichiers CustomArmor.yml et Weapon.yml ────────────────

    private void createCustomConfigs() {
        File customArmorFile = new File(getDataFolder(), "CustomArmor.yml");
        if (!customArmorFile.exists()) {
            customArmorFile.getParentFile().mkdirs();
            saveResource("CustomArmor.yml", false);
        }
        customArmorConfig = YamlConfiguration.loadConfiguration(customArmorFile);

        File weaponFile = new File(getDataFolder(), "Weapon.yml");
        if (!weaponFile.exists()) {
            weaponFile.getParentFile().mkdirs();
            saveResource("Weapon.yml", false);
        }
        weaponConfig = YamlConfiguration.loadConfiguration(weaponFile);
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

    // ── Rechargement des configurations ──────────────────────────────────

    public void reloadCustomConfigs() {
        reloadConfig(); // Recharge le config.yml par défaut

        File customArmorFile = new File(getDataFolder(), "CustomArmor.yml");
        if (customArmorFile.exists()) {
            customArmorConfig = YamlConfiguration.loadConfiguration(customArmorFile);
        }

        File weaponFile = new File(getDataFolder(), "Weapon.yml");
        if (weaponFile.exists()) {
            weaponConfig = YamlConfiguration.loadConfiguration(weaponFile);
        }
    }

    // ── Accesseurs ────────────────────────────────────────────────────────

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