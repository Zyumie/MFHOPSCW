package fr.zyumie.main.oxygen.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Lancé une seule fois au moment où la jauge d'oxygène d'un joueur tombe à 0
 * (début de la phase "suffocation": dégâts périodiques, blindness, nausée).
 */
public class PlayerOxygenDepletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;

    public PlayerOxygenDepletedEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
