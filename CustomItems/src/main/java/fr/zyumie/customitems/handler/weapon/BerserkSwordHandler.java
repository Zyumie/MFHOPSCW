package fr.zyumie.customitems.handler.weapon;

import fr.zyumie.customitems.CustomItems;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BerserkSwordHandler {

    private final CustomItems plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> activeTasks = new HashMap<>();

    public BerserkSwordHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    public void activate(Player player) {
        if (!plugin.getWeaponConfig().getBoolean("weapons.berserk-sword.enabled", true)) return;

        // Si déjà actif, on bloque pour éviter la superposition de tâches
        if (activeTasks.containsKey(player.getUniqueId())) {
            send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.already-active", "<red>Vous êtes déjà en mode Berserk !"));
            return;
        }

        long cooldownMs = plugin.getWeaponConfig().getLong("weapons.berserk-sword.cooldown-seconds", 180) * 1000L;
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < cooldownMs) {
            long remaining = (cooldownMs - (now - last)) / 1000;
            send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.cooldown", "")
                    .replace("{remaining}", String.valueOf(remaining)));
            return;
        }

        int durationSec = plugin.getWeaponConfig().getInt("weapons.berserk-sword.berserk-duration-seconds", 20);
        if (durationSec <= 0) durationSec = 20;

        int ticks = durationSec * 20; // Ex: 20s * 20 = 400 ticks

        // 1. Applique Force II et Speed I avec override = true (3e paramètre boolean de PotionEffect)
        applyBerserkEffects(player, ticks);

        // 2. Buff des membres de la même faction
        buffFactionMembers(player, ticks);

        send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.activated", "")
                .replace("{duration}", String.valueOf(durationSec)));

        // 3. Planifie la fin des effets exactement après 'ticks' (400 ticks = 20 secondes)
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deactivate(player);
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }, ticks).getTaskId();

        activeTasks.put(player.getUniqueId(), taskId);
    }

    private void deactivate(Player player) {
        removeBerserkEffects(player);
        send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.ended", ""));
        activeTasks.remove(player.getUniqueId());
    }

    private void applyBerserkEffects(Player player, int ticks) {
        // Paramètres: Type, Ticks, Amplifier, Ambient, Particles, Icon
        // L'amplificateur 1 = Force II | Amplificateur 0 = Speed I
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, true, true));
    }

    private void removeBerserkEffects(Player player) {
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    private void buffFactionMembers(Player activator, int ticks) {
        double radius = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.faction-buff-radius", 10.0);
        String factionName = getFactionOf(activator);

        for (Player nearby : activator.getWorld().getNearbyEntitiesByType(Player.class, activator.getLocation(), radius)) {
            if (nearby.equals(activator)) continue;

            String nearbyFaction = getFactionOf(nearby);
            if (factionName == null || !factionName.equals(nearbyFaction)) continue;

            applyBerserkEffects(nearby, ticks);
            send(nearby, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.faction-buffed", "")
                    .replace("{player}", activator.getName()));
        }
    }

    private String getFactionOf(Player player) {
        try {
            Class<?> apiClass = Class.forName("fr.zyumie.faction.api.FactionAPI");
            Object apiInstance = apiClass.getMethod("get").invoke(null);
            if (apiInstance == null) return null;

            Object result = apiClass.getMethod("getFactionName", UUID.class).invoke(apiInstance, player.getUniqueId());
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void cleanup(Player player) {
        Integer taskId = activeTasks.remove(player.getUniqueId());
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        removeBerserkEffects(player);
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }
}