package fr.zyumie.main.oxygen.api;

import org.bukkit.entity.Player;

/**
 * Point d'extension pour une source d'oxygène EXTERNE aux poumons du joueur
 * (typiquement: l'armure à oxygène, dans OxygenArmorPlugin).
 *
 * Tant qu'un supplier "couvre" le besoin en oxygène d'un joueur en surface,
 * la jauge de poumons (gérée par ce plugin) ne descend pas — c'est la réserve
 * du supplier qui est consommée à la place (gérée par le plugin qui l'enregistre).
 *
 * Enregistrement (depuis OxygenArmorPlugin, une fois écrit):
 *   OxygenAPI.get().registerSupplier(new ArmorOxygenSupplier(...));
 */
public interface OxygenSupplier {

    /**
     * Priorité d'examen (plus petit = examiné en premier). Permet par exemple
     * de prioriser une bouteille d'oxygène avant l'armure si plusieurs sources existent un jour.
     */
    default int priority() {
        return 100;
    }

    /**
     * Appelé une fois par tick de vérification (cf. oxygen.check-interval-ticks) pour un joueur en surface.
     *
     * @param player        le joueur concerné
     * @param percentNeeded la quantité (en %) que les poumons auraient perdu ce tick si rien ne la couvrait
     * @return true si ce supplier a couvert le besoin (les poumons ne perdent rien ce tick),
     *         false s'il n'a rien à offrir (on passe au supplier suivant, ou aux poumons en dernier recours)
     */
    boolean supplyTick(Player player, double percentNeeded);
}
