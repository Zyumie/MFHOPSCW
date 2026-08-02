package fr.zyumie.profile;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.module.Module;
import fr.zyumie.profile.command.ProfileCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class Profile extends JavaPlugin implements Module {

    @Override
    public void onEnable() {
        MainAPI.get().getModuleManager().register(this);
    }

    @Override public String getId()          { return "profile"; }
    @Override public String getDisplayName() { return "Profil Joueur"; }

    @Override
    public void onModuleEnable() {
        getCommand("profile").setExecutor(new ProfileCommand(this));
        getLogger().info("ProfileModule activé.");
    }

    @Override
    public void onModuleDisable() {
        getLogger().info("ProfileModule désactivé.");
    }
}
