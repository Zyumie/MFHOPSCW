package fr.zyumie.main.integration;

import fr.zyumie.main.Main;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Pont vers le plugin de Misane (softdepend).
 *
 * ⚠️ STUB pour le moment: on n'a pas pu confirmer le nom exact du plugin ni son API
 * (le repo GitHub fourni a renvoyé une 404 lors de la vérification). Dès qu'on a :
 *   - le nom exact retourné par getDescription().getName()
 *   - les classes/méthodes/events qu'il expose publiquement
 * ... on remplit cette classe pour, par exemple, écouter ses events ou appeler son API.
 *
 * Le plugin est chargé en softdepend (voir plugin.yml), donc le Main démarre même
 * si ce plugin est absent du serveur — isPresent() sera simplement false.
 */
public class MisaneBridge {

    private final Main plugin;
    private Plugin misanePlugin;
    private boolean present = false;

    public MisaneBridge(Main plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        boolean enabledInConfig = plugin.getConfig().getBoolean("integration.misane.enabled", true);
        if (!enabledInConfig) {
            plugin.getLogger().info("Intégration Misane désactivée via config.yml.");
            return;
        }

        String pluginName = plugin.getConfig().getString("integration.misane.plugin-name", "NewV5");
        misanePlugin = Bukkit.getPluginManager().getPlugin(pluginName);

        if (misanePlugin == null) {
            plugin.getLogger().info("Plugin Misane ('" + pluginName + "') non trouvé sur le serveur — intégration ignorée (softdepend).");
            present = false;
            return;
        }

        present = true;
        plugin.getLogger().info("Plugin Misane détecté: " + misanePlugin.getName() + " v" + misanePlugin.getDescription().getVersion());

        // TODO: une fois l'API connue, par exemple:
        //   if (misanePlugin instanceof SomeMisaneClass api) { ... }
        // ou écoute d'events custom qu'il publie.
    }

    public boolean isPresent() {
        return present;
    }

    public Plugin getMisanePlugin() {
        return misanePlugin;
    }
}
