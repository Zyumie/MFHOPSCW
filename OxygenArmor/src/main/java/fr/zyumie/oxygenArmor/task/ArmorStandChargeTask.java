package fr.zyumie.oxygenArmor.task;

import fr.zyumie.oxygenArmor.OxygenArmor;
import fr.zyumie.oxygenArmor.util.ArmorItemUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class ArmorStandChargeTask extends BukkitRunnable {

    private final OxygenArmor plugin;

    public ArmorStandChargeTask(OxygenArmor plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        int intervalTicks   = plugin.getConfig().getInt("armor.recharge.tick-interval-ticks", 100);
        int totalSeconds    = plugin.getConfig().getInt("armor.recharge.duration-seconds", 7200);
        int radiusChunks    = plugin.getConfig().getInt("armor.recharge.player-detection-radius-chunks", 0);

        // % gagné à chaque exécution de cette tâche
        long totalTicks = (long) totalSeconds * 20;
        double percentPerRun = (100.0 * intervalTicks) / totalTicks;

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                // Optim lag : on ne traite que si un joueur est dans la zone
                if (!hasPlayerNearby(chunk, radiusChunks)) continue;

                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof ArmorStand stand)) continue;
                    processStand(stand, percentPerRun);
                }
            }
        }
    }

    private void processStand(ArmorStand stand, double percentPerRun) {
        ItemStack chest = stand.getEquipment().getChestplate();
        if (!ArmorItemUtil.isOxygenArmor(plugin, chest)) return;

        double current = ArmorItemUtil.readPercent(plugin, chest);
        if (current >= 100.0) return;

        double updated = Math.min(100.0, current + percentPerRun);
        ArmorItemUtil.updatePercent(plugin, chest, updated);
        stand.getEquipment().setChestplate(chest);

        if (updated >= 100.0) {
            notifyNearby(stand);
        }
    }

    private boolean hasPlayerNearby(Chunk chunk, int radius) {
        int cx = chunk.getX(), cz = chunk.getZ();
        World w = chunk.getWorld();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(w)) continue;
            Chunk pc = p.getLocation().getChunk();
            if (Math.abs(pc.getX() - cx) <= radius && Math.abs(pc.getZ() - cz) <= radius)
                return true;
        }
        return false;
    }

    private void notifyNearby(ArmorStand stand) {
        String raw = plugin.getConfig().getString("messages.recharge-complete", "");
        var msg = MiniMessage.miniMessage().deserialize(raw);
        stand.getLocation().getNearbyPlayers(16).forEach(p -> p.sendMessage(msg));
    }
}
