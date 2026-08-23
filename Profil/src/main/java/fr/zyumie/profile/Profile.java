package fr.zyumie.profile;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.profile.command.ProfileCommand;
import fr.zyumie.profile.gui.ProfileGuiManager;
import fr.zyumie.profile.listener.ProfileGuiListener;
import org.bukkit.plugin.java.JavaPlugin;

public class Profile extends JavaPlugin implements Module {

    private ProfileGuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        guiManager = new ProfileGuiManager(this); // charge (ou crée) gui.yml
        MainAPI.get().getModuleManager().register(this);
    }

    @Override public String getId()          { return "profile"; }
    @Override public String getDisplayName() { return "Profil Joueur"; }

    @Override
    public void onModuleEnable() {
        ProfileCommand profileCommand = new ProfileCommand(this, guiManager);
        getCommand("profile").setExecutor(profileCommand);
        getCommand("profile").setTabCompleter(profileCommand);
        getServer().getPluginManager().registerEvents(new ProfileGuiListener(), this);
        getLogger().info("ProfileModule activé.");
    }

    @Override
    public void onModuleDisable() {
        getLogger().info("ProfileModule désactivé.");
    }
}
