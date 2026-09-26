package fr.zyumie.main.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Déclenché par le module Storm à chaque début ou fin de tempête.
 *
 * Permet à n'importe quel autre système du Main (ex: le verrou "storm-lockout",
 * ou un futur module qui réagit à la météo) de connaître l'état de la tempête
 * SANS dépendre directement des classes internes du module Storm — uniquement
 * de cet event qui vit dans le Main, sur le même principe que {@link OxygenChangeEvent}.
 *
 * Côté module Storm, il suffit d'appeler, à chaque changement d'état :
 *   Bukkit.getPluginManager().callEvent(new StormStateChangeEvent(true));  // la tempête démarre
 *   Bukkit.getPluginManager().callEvent(new StormStateChangeEvent(false)); // la tempête se termine
 */
public class StormStateChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean stormActive;

    public StormStateChangeEvent(boolean stormActive) {
        this.stormActive = stormActive;
    }

    public boolean isStormActive() {
        return stormActive;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
