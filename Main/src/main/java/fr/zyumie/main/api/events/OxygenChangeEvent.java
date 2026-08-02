package fr.zyumie.main.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Déclenché par le module Oxygène à chaque fois que la jauge d'un joueur change
 * (perte en surface, gain sous terre, reset, etc).
 *
 * Le futur module Armure Oxygène écoutera cet event pour mettre à jour la texture
 * de l'armure (blanc -> noir selon le %) SANS dépendre directement des classes
 * internes du module Oxygène — uniquement de cet event qui vit dans le Main.
 */
public class OxygenChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final double previousPercentage;
    private final double newPercentage;

    public OxygenChangeEvent(Player player, double previousPercentage, double newPercentage) {
        this.player = player;
        this.previousPercentage = previousPercentage;
        this.newPercentage = newPercentage;
    }

    public Player getPlayer() {
        return player;
    }

    public double getPreviousPercentage() {
        return previousPercentage;
    }

    public double getNewPercentage() {
        return newPercentage;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
