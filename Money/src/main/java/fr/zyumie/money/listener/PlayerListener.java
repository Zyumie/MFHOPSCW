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
        manager.loadPlayer(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.unload(e.getPlayer().getUniqueId());
    }
}
