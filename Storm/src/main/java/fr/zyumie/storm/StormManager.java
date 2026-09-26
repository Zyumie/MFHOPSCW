package fr.zyumie.storm;

import fr.zyumie.main.api.events.StormStateChangeEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class StormManager {

    private final Storm plugin;
    private boolean stormActive = false;

    private BukkitTask nightLockTask;
    private BukkitTask playerCheckTask;
    private BukkitTask damageTask;

    private final ConcurrentHashMap<UUID, Long> warningCooldown = new ConcurrentHashMap<>();

    /**
     * Checker d'immunité injecté par CustomItems via réflexion.
     * Par défaut : personne n'est immunisé.
     */
    private Predicate<Player> immunityChecker = p -> false;

    public StormManager(Storm plugin) {
        this.plugin = plugin;
    }

    /** Appelé par CustomItems pour brancher l'armure anti-tempête. */
    public void setImmunityChecker(Predicate<Player> checker) {
        this.immunityChecker = checker != null ? checker : p -> false;
    }

    // ── Démarrage / Arrêt ─────────────────────────────────────────────

    public void startStorm(boolean broadcast) {
        if (stormActive) return;
        stormActive = true;

        for (World world : getAffectedWorlds()) activateWeather(world);

        if (plugin.getConfig().getBoolean("environment.eternal-night", true)) {
            long nightTime = plugin.getConfig().getLong("environment.night-time", 18000L);
            nightLockTask = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                getAffectedWorlds().forEach(w -> w.setFullTime(nightTime)), 0L, 20L);
        }

        long checkInterval = plugin.getConfig().getLong("exposure.check-interval-ticks", 20L);
        playerCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isAffectedWorld(player.getWorld())) continue;
                if (shouldIgnore(player)) continue;
                if (immunityChecker.test(player)) continue; // ← Armure anti-tempête
                if (isExposedToSky(player)) {
                    applyExposureEffects(player);
                    maybeLightning(player);
                }
            }
        }, 0L, checkInterval);

        long dmgInterval = plugin.getConfig().getLong("exposure.damage-interval-ticks", 40L);
        double dmgAmount  = plugin.getConfig().getDouble("exposure.damage-amount", 2.0);
        damageTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isAffectedWorld(player.getWorld())) continue;
                if (player.getGameMode() == GameMode.SPECTATOR) continue;
                if (immunityChecker.test(player)) continue; // ← Armure anti-tempête
                if (!isExposedToSky(player)) continue;
                player.setNoDamageTicks(0);
                player.damage(dmgAmount);
            }
        }, dmgInterval, dmgInterval);

        if (broadcast) broadcastLines("messages.storm-start");
        plugin.getLogger().info("⛈ Tempête démarrée.");

        // Notifie le reste de l'écosystème (ex: storm-lockout du Main) que la tempête démarre.
        Bukkit.getPluginManager().callEvent(new StormStateChangeEvent(true));
    }

    public void stopStorm(boolean broadcast) {
        if (!stormActive) return;
        stormActive = false;
        if (nightLockTask   != null) { nightLockTask.cancel();   nightLockTask   = null; }
        if (playerCheckTask != null) { playerCheckTask.cancel(); playerCheckTask = null; }
        if (damageTask      != null) { damageTask.cancel();      damageTask      = null; }
        warningCooldown.clear();
        for (World world : getAffectedWorlds()) deactivateWeather(world);
        Bukkit.getOnlinePlayers().forEach(this::clearExposureEffects);
        if (broadcast) broadcastLines("messages.storm-end");
        plugin.getLogger().info("☀ Tempête terminée.");

        // Notifie le reste de l'écosystème (ex: storm-lockout du Main) que la tempête se termine.
        Bukkit.getPluginManager().callEvent(new StormStateChangeEvent(false));
    }

    // ── Environnement ─────────────────────────────────────────────────

    private void activateWeather(World world) {
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE,  false);
        if (plugin.getConfig().getBoolean("environment.thunderstorm", true)) {
            world.setStorm(true);
            world.setThundering(true);
            world.setWeatherDuration(Integer.MAX_VALUE);
            world.setThunderDuration(Integer.MAX_VALUE);
        }
    }

    private void deactivateWeather(World world) {
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE,  true);
        world.setStorm(false);
        world.setThundering(false);
    }

    // ── Détection surface ─────────────────────────────────────────────

    private boolean isExposedToSky(Player player) {
        var loc  = player.getEyeLocation();
        var world = loc.getWorld();
        if (world == null) return false;
        int x = loc.getBlockX(), z = loc.getBlockZ(), eyeY = loc.getBlockY();
        for (int y = eyeY + 1; y < world.getMaxHeight(); y++) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isOccluding()) return false;
        }
        return true;
    }

    // ── Effets joueurs ────────────────────────────────────────────────

    private void applyExposureEffects(Player player) {
        int dur      = plugin.getConfig().getInt("exposure.effect-duration-ticks", 60);
        int nausea   = plugin.getConfig().getInt("exposure.nausea-amplifier", 0);
        int slow     = plugin.getConfig().getInt("exposure.slowness-amplifier", 2);
        int wither   = plugin.getConfig().getInt("exposure.wither-amplifier", -1);
        boolean blind= plugin.getConfig().getBoolean("exposure.blindness", true);

        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,    dur, nausea, false, true));
        if (slow   >= 0) player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, slow, false, true));
        if (wither >= 0) player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,   dur, wither, false, true));
        if (blind)       player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,dur, 0, false, true));

        long now = System.currentTimeMillis();
        long cd  = plugin.getConfig().getLong("messages.surface-warning-interval-ticks", 100L) * 50L;
        if (now - warningCooldown.getOrDefault(player.getUniqueId(), 0L) >= cd) {
            warningCooldown.put(player.getUniqueId(), now);
            player.sendMessage(mm(prefix() + plugin.getConfig().getString("messages.surface-warning", "")));
        }
    }

    private void clearExposureEffects(Player player) {
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WITHER);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    private void maybeLightning(Player player) {
        if (!plugin.getConfig().getBoolean("hazards.lightning-strikes", true)) return;
        if (Math.random() < plugin.getConfig().getDouble("hazards.lightning-chance", 0.05))
            player.getWorld().strikeLightning(player.getLocation());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private boolean shouldIgnore(Player player) {
        if (plugin.getConfig().getBoolean("exposure.ignore-creative", true)
                && (player.getGameMode() == GameMode.CREATIVE
                 || player.getGameMode() == GameMode.SPECTATOR)) return true;
        if (plugin.getConfig().getBoolean("exposure.use-bypass-permission", true)
                && player.hasPermission("storm.bypass")) return true;
        return false;
    }

    private List<World> getAffectedWorlds() {
        List<World> worlds = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("worlds")) {
            World w = Bukkit.getWorld(name);
            if (w != null) worlds.add(w);
        }
        return worlds;
    }

    private boolean isAffectedWorld(World world) {
        return plugin.getConfig().getStringList("worlds").contains(world.getName());
    }

    private void broadcastLines(String path) {
        List<String> lines = plugin.getConfig().getStringList(path);
        String endDate = plugin.getScheduler().getCurrentState() != null
                ? plugin.getScheduler().getCurrentState().getEndDateFormatted() : "N/A";
        long dur = plugin.getScheduler().getCurrentState() != null
                ? plugin.getScheduler().getCurrentState().getDurationDays() : 1L;
        for (Player p : Bukkit.getOnlinePlayers())
            for (String line : lines)
                p.sendMessage(mm(prefix() + line
                        .replace("{end-date}", endDate)
                        .replace("{duration}", String.valueOf(dur))));
    }

    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "&8[&9⛈&8] &7");
    }

    private net.kyori.adventure.text.Component mm(String legacy) {
        return MiniMessage.miniMessage().deserialize(
                legacy.replace("&8","<dark_gray>").replace("&9","<blue>")
                      .replace("&a","<green>").replace("&c","<red>")
                      .replace("&e","<yellow>").replace("&7","<gray>")
                      .replace("&f","<white>").replace("&l","<bold>"));
    }

    public boolean isStormActive() { return stormActive; }
    public void cleanup()          { stopStorm(false); }
}
