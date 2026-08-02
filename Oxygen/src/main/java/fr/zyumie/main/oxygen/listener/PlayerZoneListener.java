package fr.zyumie.main.oxygen.listener;

import fr.zyumie.main.oxygen.OxygenManager;
import fr.zyumie.main.oxygen.Oxygen;
import fr.zyumie.main.oxygen.util.ChatUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerZoneListener implements Listener {

    private final Oxygen plugin;
    private final OxygenManager manager;

    public PlayerZoneListener(Oxygen plugin, OxygenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // On ne traite que les changements de bloc Y pour éviter de spammer chaque micro-mouvement
        if (event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        var player = event.getPlayer();
        int threshold = plugin.getConfig().getInt("oxygen.surface-y-threshold", 60);

        int fromY = event.getFrom().getBlockY();
        int toY = event.getTo().getBlockY();

        boolean wasUnderground = fromY < threshold;
        boolean isNowSurface = toY >= threshold;

        // Joueur vient de passer en surface
        if (wasUnderground && isNowSurface) {
            String prefix = plugin.getConfig().getString("messages.prefix", "");
            String warning = plugin.getConfig().getString("messages.surface-warning", "");
            if (warning != null && !warning.isEmpty()) {
                player.sendMessage(ChatUtil.color(prefix + warning));
            }
        }
    }
}
