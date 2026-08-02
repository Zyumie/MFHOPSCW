package fr.zyumie.main.oxygen.task;

import fr.zyumie.main.oxygen.OxygenManager;
import fr.zyumie.main.oxygen.Oxygen;
import fr.zyumie.main.oxygen.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class OxygenTickTask extends BukkitRunnable {

    private final Oxygen plugin;
    private final OxygenManager manager;

    public OxygenTickTask(Oxygen plugin, OxygenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {
        double depletionPerSecond = plugin.getConfig().getDouble("oxygen.depletion-rate-percent-per-second", 1.0);
        double regenPerSecond = plugin.getConfig().getDouble("oxygen.regen-rate-percent-per-second", 5.0);
        int checkIntervalTicks = plugin.getConfig().getInt("oxygen.check-interval-ticks", 20);
        // ramène le taux par seconde à un taux par tick d'exécution de cette tâche
        double secondsPerCheck = checkIntervalTicks / 20.0;
        double depletionPerCheck = depletionPerSecond * secondsPerCheck;
        double regenPerCheck = regenPerSecond * secondsPerCheck;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (manager.getData(player) == null) continue;

            boolean surface = manager.isSurface(player);

            if (surface) {
                boolean coveredExternally = manager.trySupplyExternally(player, depletionPerCheck);
                if (!coveredExternally) {
                    manager.modify(player, -depletionPerCheck);
                }

                if (manager.getPercent(player) <= 0.0) {
                    applySuffocation(player);
                }
            } else {
                // Sous terre: air naturel, régénération de la jauge
                if (manager.getPercent(player) < 100.0) {
                    manager.modify(player, regenPerCheck);
                }
            }
        }
    }

    private void applySuffocation(Player player) {
        int intervalSeconds = plugin.getConfig().getInt("oxygen.suffocation.damage-interval-seconds", 3);
        long intervalMillis = intervalSeconds * 1000L;
        long now = System.currentTimeMillis();

        var data = manager.getData(player);
        if (now - data.getLastDamageTickMillis() < intervalMillis) {
            return; // pas encore le moment d'infliger des dégâts
        }
        data.setLastDamageTickMillis(now);

        int hearts = plugin.getConfig().getInt("oxygen.suffocation.damage-hearts", 2);
        double damage = hearts * 2.0; // 1 cœur = 2 points de vie

        player.damage(damage);

        int blindnessTicks = plugin.getConfig().getInt("oxygen.suffocation.blindness-duration-ticks", 100);
        int nauseaTicks = plugin.getConfig().getInt("oxygen.suffocation.nausea-duration-ticks", 100);
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaTicks, 0, false, true));

        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String warning = plugin.getConfig().getString("messages.death-imminent", "");
        if (warning != null && !warning.isEmpty()) {
            player.sendMessage(ChatUtil.color(prefix + warning));
        }
    }
}
