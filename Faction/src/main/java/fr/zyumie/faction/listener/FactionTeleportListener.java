package fr.zyumie.faction.listener;

import fr.zyumie.faction.command.FactionCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class FactionTeleportListener implements Listener {

    private final FactionCommand factionCommand;

    public FactionTeleportListener(FactionCommand factionCommand) {
        this.factionCommand = factionCommand;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Annule la TP si le joueur se déplace de bloc (change de X, Y ou Z)
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            factionCommand.cancelPendingTeleport(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            factionCommand.cancelPendingTeleport(player.getUniqueId(), true);
        }
    }
}