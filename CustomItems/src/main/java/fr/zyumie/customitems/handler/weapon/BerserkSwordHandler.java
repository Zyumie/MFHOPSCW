package fr.zyumie.customitems.handler.weapon;

import fr.zyumie.customitems.CustomItems;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BerserkSwordHandler {

    private static final String MOD_NAME = "berserk_attack_speed";

    private final CustomItems plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> activeTasks = new HashMap<>();

    public BerserkSwordHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    public void activate(Player player) {
        if (!plugin.getWeaponConfig().getBoolean("weapons.berserk-sword.enabled", true)) return;

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

        int ticks = durationSec * 20; // 20s * 20 = 400 ticks
        double atkSpd = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.attack-speed-value", 1024.0);

        // 1. Applique le mode 1.8 (Attack Speed) + Potions (Force I + Speed I)
        applyBerserkBuffs(player, ticks, atkSpd);

        // 2. Transmet les buffs aux membres de la faction proches
        buffFactionMembers(player, ticks, atkSpd);

        send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.activated", "")
                .replace("{duration}", String.valueOf(durationSec)));

        // Tâche de fin
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deactivate(player);
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }, ticks).getTaskId();

        activeTasks.put(player.getUniqueId(), taskId);
    }

    private void deactivate(Player player) {
        removeBerserkBuffs(player);
        send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.ended", ""));
        activeTasks.remove(player.getUniqueId());
    }

    private void applyBerserkBuffs(Player player, int ticks, double attackSpeedValue) {
        // --- PvP 1.8 (Attaque instantanée sans cooldown) ---
        var attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr != null) {
            NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
            attr.getModifiers().stream().filter(m -> m.key().equals(key)).toList().forEach(attr::removeModifier);
            attr.addModifier(new AttributeModifier(key, attackSpeedValue, AttributeModifier.Operation.ADD_NUMBER));
        }

        // --- Effets de Potions (Force I = amplifier 0, Speed I = amplifier 0) ---
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, true, true));
    }

    private void removeBerserkBuffs(Player player) {
        // Retire l'attribut d'Attack Speed 1.8
        var attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr != null) {
            NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
            attr.getModifiers().stream().filter(m -> m.key().equals(key)).toList().forEach(attr::removeModifier);
        }

        // Retire les effets de potion
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    private void buffFactionMembers(Player activator, int ticks, double attackSpeedValue) {
        double radius = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.faction-buff-radius", 10.0);
        String factionName = getFactionOf(activator);

        for (Player nearby : activator.getWorld().getNearbyEntitiesByType(Player.class, activator.getLocation(), radius)) {
            if (nearby.equals(activator)) continue;

            String nearbyFaction = getFactionOf(nearby);
            if (factionName == null || !factionName.equals(nearbyFaction)) continue;

            applyBerserkBuffs(nearby, ticks, attackSpeedValue);
            send(nearby, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.faction-buffed", "")
                    .replace("{player}", activator.getName()));

            // Planifie le retrait des buffs d'attributs spécifiquement pour l'allié
            Bukkit.getScheduler().runTaskLater(plugin, () -> removeBerserkBuffs(nearby), ticks);
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
        removeBerserkBuffs(player);
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }
}