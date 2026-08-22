package fr.zyumie.money.listener;

import fr.zyumie.money.MoneyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final MoneyManager manager;

    public PlayerListener(MoneyManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Chargement async : évite de bloquer le thread principal avec une lecture disque
        // à chaque connexion. getBalance() garde un filet de sécurité synchrone au besoin.
        manager.loadPlayerAsync(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.unload(e.getPlayer().getUniqueId());
    }
}
