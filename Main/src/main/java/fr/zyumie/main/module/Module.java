package fr.zyumie.main.module;

/**
 * Interface que chaque sous-plugin (Oxygène, Faction, HDV, ...) doit implémenter
 * et enregistrer auprès du {@link ModuleManager} via {@code MainAPI.get().getModuleManager().register(...)}.
 *
 * Le sous-plugin reste un .jar Bukkit à part entière (avec son propre onEnable),
 * mais il s'enregistre ici pour bénéficier de :
 *  - l'activation/désactivation centralisée depuis config.yml du Main
 *  - le stockage de données (YAML ou MySQL selon la config globale)
 *  - la communication inter-modules (ex: Faction lit l'argent via l'API d'un autre module)
 */
public interface Module {

    /**
     * Identifiant unique du module, doit correspondre à une clé sous "modules:" dans config.yml du Main.
     * Exemple: "oxygen", "faction", "hdv"
     */
    String getId();

    /**
     * Nom affichable (logs, commandes admin)
     */
    String getDisplayName();

    /**
     * Appelé par le ModuleManager une fois que le module est autorisé à démarrer
     * (config.yml du Main dit modules.<id>: true).
     * C'est ici que le sous-plugin doit faire son setup réel (listeners, tasks, etc.),
     * PAS dans son propre onEnable() qui doit rester minimal.
     */
    void onModuleEnable();

    /**
     * Appelé lors d'un /mainadmin reload ou d'un arrêt serveur.
     */
    void onModuleDisable();

    /**
     * Dépendances logiques vers d'autres modules (par leur id).
     * Le ModuleManager refusera d'activer ce module si une dépendance est absente/désactivée.
     * Exemple: FactionModule peut dépendre de [] (rien), ProfileModule peut dépendre de ["faction"]
     */
    default String[] getDependencies() {
        return new String[0];
    }
}
