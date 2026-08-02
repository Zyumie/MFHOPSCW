package fr.zyumie.main.module;

import fr.zyumie.main.Main;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

public class ModuleManager {

    private final Main plugin;
    private final Map<String, Module> registered = new LinkedHashMap<>();
    private final Set<String> enabled = new LinkedHashSet<>();

    public ModuleManager(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Appelée par chaque sous-plugin dans son onEnable():
     *
     *   MainAPI.get().getModuleManager().register(new OxygenModule(this));
     *
     * Si MainPlugin n'est pas encore prêt (ordre de chargement), le sous-plugin
     * doit avoir "depend: [MainPlugin]" dans son plugin.yml pour garantir l'ordre.
     */
    public void register(Module module) {
        if (registered.containsKey(module.getId())) {
            plugin.getLogger().warning("Module '" + module.getId() + "' déjà enregistré, ignoré.");
            return;
        }
        registered.put(module.getId(), module);
        plugin.getLogger().info("Module enregistré: " + module.getDisplayName() + " (id=" + module.getId() + ")");
        tryEnable(module);
    }

    public void unregister(String id) {
        Module m = registered.remove(id);
        if (m != null && enabled.contains(id)) {
            m.onModuleDisable();
            enabled.remove(id);
        }
    }

    private boolean isAllowedInConfig(String id) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("modules");
        if (section == null) return true; // par défaut autorisé si pas spécifié
        return section.getBoolean(id, true);
    }

    private void tryEnable(Module module) {
        String id = module.getId();

        if (!isAllowedInConfig(id)) {
            plugin.getLogger().info("Module '" + id + "' désactivé via config.yml, non démarré.");
            return;
        }

        for (String dep : module.getDependencies()) {
            if (!enabled.contains(dep)) {
                plugin.getLogger().severe("Module '" + id + "' nécessite '" + dep
                        + "' qui n'est pas encore activé. Vérifie l'ordre de chargement (depend: dans plugin.yml).");
                return;
            }
        }

        try {
            module.onModuleEnable();
            enabled.add(id);
            plugin.getLogger().info("Module '" + module.getDisplayName() + "' activé.");
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de l'activation du module '" + id + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Recharge tous les modules (utilisé par /mainadmin reload) */
    public void reloadAll() {
        for (String id : new LinkedHashSet<>(enabled)) {
            Module m = registered.get(id);
            if (m != null) m.onModuleDisable();
        }
        enabled.clear();
        for (Module m : registered.values()) {
            tryEnable(m);
        }
    }

    public void disableAll() {
        for (String id : new LinkedHashSet<>(enabled)) {
            Module m = registered.get(id);
            if (m != null) m.onModuleDisable();
        }
        enabled.clear();
    }

    public boolean isEnabled(String id) {
        return enabled.contains(id);
    }

    public Module get(String id) {
        return registered.get(id);
    }

    public Map<String, Module> getRegistered() {
        return registered;
    }
}
