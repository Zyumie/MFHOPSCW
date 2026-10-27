package fr.zyumie.faction;

import fr.zyumie.faction.model.FactionGrade;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class GradeHelper {

    private final JavaPlugin plugin;

    public GradeHelper(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getDisplay(FactionGrade grade) {
        return plugin.getConfig().getString("grades." + grade.name() + ".display", grade.name());
    }

    public String getColor(FactionGrade grade) {
        return plugin.getConfig().getString("grades." + grade.name() + ".color", "<gray>");
    }

    public String coloredDisplay(FactionGrade grade) {
        return getColor(grade) + getDisplay(grade);
    }

    public boolean hasPerm(FactionGrade grade, String permission) {
        if (grade == null) return false;

        // Le CHEF a toutes les permissions automatiquement
        if (grade == FactionGrade.CHEF) {
            return true;
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("grades." + grade.name());
        if (section == null) return false;
        List<String> perms = section.getStringList("permissions");
        return perms.contains(permission);
    }
}