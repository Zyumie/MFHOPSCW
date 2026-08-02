package fr.zyumie.main.oxygen.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Lancé chaque fois que la jauge d'oxygène des POUMONS (pas celle de l'armure) d'un joueur change.
 * Utilisé par ex. par le futur ProfileModule pour afficher l'oxygène dans le profil,
 * ou par OxygenArmorPlugin pour synchroniser l'affichage de la barre.
 */
public class OxygenChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final double oldValue;
    private final double newValue;

    public OxygenChangeEvent(Player player, double oldValue, double newValue) {
        this.player = player;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Player getPlayer() {
        return player;
    }

    public double getOldValue() {
        return oldValue;
    }

    public double getNewValue() {
        return newValue;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
