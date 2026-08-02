package fr.zyumie.oxygenArmor.task;

import fr.zyumie.oxygenArmor.ArmorManager;
import fr.zyumie.oxygenArmor.OxygenArmor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ArmorDisplayTask extends BukkitRunnable {

    private final ArmorManager manager;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public ArmorDisplayTask(ArmorManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (manager.isWearingArmor(p)) {
                showBar(p);
            } else {
                hideBar(p);
            }
        }
    }

    private void showBar(Player p) {
        double pct = manager.getReserve(p);
        float progress = (float) Math.max(0, Math.min(1, pct / 100.0));

        BossBar.Color color = pct > 50 ? BossBar.Color.WHITE
                : pct > 20 ? BossBar.Color.YELLOW
                : BossBar.Color.RED;

        var title = MiniMessage.miniMessage().deserialize(
                "<gray>O₂ </gray><white>" + String.format("%.0f", pct) + "%");

        BossBar bar = bars.computeIfAbsent(p.getUniqueId(),
                u -> BossBar.bossBar(title, progress, color, BossBar.Overlay.PROGRESS));

        bar.name(title);
        bar.progress(progress);
        bar.color(color);
        p.showBossBar(bar);
    }

    private void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    public void cleanup() {
        Bukkit.getOnlinePlayers().forEach(this::hideBar);
        bars.clear();
    }
}
