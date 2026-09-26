package fr.zyumie.main.module;

import fr.zyumie.main.Main;
import fr.zyumie.main.api.events.StormStateChangeEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Écoute {@link StormStateChangeEvent} (envoyé par le module Storm) et désactive
 * automatiquement, le temps de la tempête, les modules listés dans la section
 * "storm-lockout" de config.yml — puis les réactive à la fin de la tempête.
 *
 * Ne connaît rien des classes internes du module Storm : uniquement de l'event,
 * sur le même principe de découplage que {@link fr.zyumie.main.integration.MisaneBridge}.
 */
public class StormLockoutManager implements Listener {

    private final Main plugin;

    // Modules qu'on a nous-mêmes coupés à cause de la tempête — pour ne réactiver
    // que ceux-là à la fin (et pas un module désactivé pour une autre raison).
    private final Set<String> lockedOut = new LinkedHashSet<>();

    private boolean stormActive = false;

    public StormLockoutManager(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onStormStateChange(StormStateChangeEvent event) {
        this.stormActive = event.isStormActive();
        if (stormActive) {
            lockDown();
        } else {
            restore();
        }
    }

    /**
     * À rappeler après un /mainadmin reload : ModuleManager#reloadAll() réactive tous
     * les modules autorisés en config sans savoir qu'une tempête est en cours. Si c'est
     * le cas, on réapplique le verrou immédiatement.
     */
    public void reapplyIfStormActive() {
        if (stormActive) {
            lockDown();
        }
    }

    private void lockDown() {
        for (String id : getConfiguredModuleIds()) {
            if (plugin.getModuleManager().isEnabled(id)) {
                plugin.getModuleManager().disable(id);
                lockedOut.add(id);
            }
        }
        if (!lockedOut.isEmpty()) {
            plugin.getLogger().info("Tempête en cours : modules mis en pause -> " + lockedOut);
        }
    }

    private void restore() {
        if (lockedOut.isEmpty()) return;
        plugin.getLogger().info("Fin de tempête : réactivation des modules -> " + lockedOut);
        for (String id : lockedOut) {
            plugin.getModuleManager().enable(id);
        }
        lockedOut.clear();
    }

    /** Lit "storm-lockout" dans config.yml — seules les entrées à `false` sont concernées. */
    private Set<String> getConfiguredModuleIds() {
        Set<String> ids = new LinkedHashSet<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("storm-lockout");
        if (section == null) return ids;
        for (String key : section.getKeys(false)) {
            if (!section.getBoolean(key, true)) {
                ids.add(key);
            }
        }
        return ids;
    }
}
