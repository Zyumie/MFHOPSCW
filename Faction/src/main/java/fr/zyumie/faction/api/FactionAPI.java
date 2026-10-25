package fr.zyumie.faction.api;

import fr.zyumie.faction.listener.FactionManager;
import fr.zyumie.faction.model.Faction;

import java.util.UUID;

public final class FactionAPI {

    private static FactionAPI instance;
    private final FactionManager manager;

    private FactionAPI(FactionManager manager) {
        this.manager = manager;
    }

    public static void init(FactionManager manager) {
        instance = new FactionAPI(manager);
    }

    public static FactionAPI get() {
        if (instance == null)
            throw new IllegalStateException("FactionAPI non initialisée.");
        return instance;
    }

    // ── Méthodes appelées par ProfilePlugin via réflexion ──

    /** Retourne le nom de la faction du joueur, ou null si aucune. */
    public String getFactionName(UUID playerUuid) {
        Faction f = manager.getFactionOf(playerUuid);
        return f != null ? f.getName() : null;
    }

    /** Retourne le solde de la bank de la faction du joueur, ou -1 si aucune. */
    public double getFactionBank(UUID playerUuid) {
        Faction f = manager.getFactionOf(playerUuid);
        return f != null ? f.getBankBalance() : -1;
    }

    public FactionManager getManager() {
        return manager;
    }
}
