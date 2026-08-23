package fr.zyumie.profile;

import org.bukkit.OfflinePlayer;

/**
 * Données d'un profil déjà résolues (argent, faction, banque), calculées une seule fois par
 * /profile puis consommées à la fois par l'affichage chat et l'affichage GUI — évite de
 * ré-interroger Vault/Faction deux fois pour la même commande.
 * <p>
 * Les champs {@code *Fallback} indiquent si la valeur correspondante est un texte de repli
 * (ex: "Aucune faction") plutôt qu'une vraie donnée, pour permettre au rendu chat de les
 * afficher dans une couleur différente.
 */
public record ProfileData(
        OfflinePlayer target,
        boolean online,
        String money, boolean moneyFallback,
        String faction, boolean factionFallback,
        String bank, boolean bankFallback
) {}
