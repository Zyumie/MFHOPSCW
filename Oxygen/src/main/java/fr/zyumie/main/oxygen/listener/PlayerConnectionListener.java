package fr.zyumie.main.oxygen.listener;

import fr.zyumie.main.oxygen.OxygenManager;
import fr.zyumie.main.oxygen.Oxygen;
import fr.zyumie.main.oxygen.util.ChatUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerConnectionListener implements Listener {

    private final Oxygen plugin;
    private final OxygenManager manager;

    public PlayerConnectionListener(Oxygen plugin, OxygenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Chargement async puis retour sur le thread principal pour éviter de bloquer la connexion
        var player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            manager.loadPlayer(player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Sauvegarde synchrone à la déconnexion pour ne rien perdre
        manager.unloadPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        var player = event.getEntity();
        // À la mort, on remet la jauge à 100% (respawn propre)
        manager.setPercent(player, 100.0);

        // Petit message si mort par asphyxie (jauge vide en surface)
        if (manager.getData(player).isSuffocating()) {
            String prefix = plugin.getConfig().getString("messages.prefix", "");
            String msg = plugin.getConfig().getString("messages.surface-warning", "");
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(ChatUtil.color(prefix + msg));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        // S'assure que la donnée est bien à 100% après le respawn
        manager.setPercent(player, 100.0);
    }
}
