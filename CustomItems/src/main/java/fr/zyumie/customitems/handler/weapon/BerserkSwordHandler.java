package fr.zyumie.customitems.handler.weapon;

import fr.zyumie.customitems.CustomItems;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

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
            send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.already-active", "Vous êtes déjà en mode Berserk !"));
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

        int duration = plugin.getWeaponConfig().getInt("weapons.berserk-sword.berserk-duration-seconds", 20);
        double atkSpd = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.attack-speed-value", 1024.0);

        applyAttackSpeed(player, atkSpd);
        buffFactionMembers(player);

        send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.activated", "")
                .replace("{duration}", String.valueOf(duration)));

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            deactivate(player);
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            activeTasks.remove(player.getUniqueId());
        }, duration * 20L).getTaskId();

        activeTasks.put(player.getUniqueId(), taskId);
    }

    private void deactivate(Player player) {
        removeAttackSpeed(player);
        send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.ended", ""));
        activeTasks.remove(player.getUniqueId());
    }

    private void applyAttackSpeed(Player player, double value) {
        var attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;

        NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
        attr.getModifiers().stream().filter(m -> m.key().equals(key)).toList().forEach(attr::removeModifier);

        attr.addModifier(new AttributeModifier(key, value, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeAttackSpeed(Player player) {
        var attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;
        NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
        attr.getModifiers().stream().filter(m -> m.key().equals(key)).toList().forEach(attr::removeModifier);
    }

    private void buffFactionMembers(Player activator) {
        double radius = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.faction-buff-radius", 10.0);
        double atkSpd = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.attack-speed-value", 1024.0);
        int duration  = plugin.getWeaponConfig().getInt("weapons.berserk-sword.berserk-duration-seconds", 20);
        String factionName = getFactionOf(activator);

        for (Player nearby : activator.getWorld().getNearbyEntitiesByType(Player.class, activator.getLocation(), radius)) {
            if (nearby.equals(activator)) continue;
            String nearbyFaction = getFactionOf(nearby);
            if (factionName == null || !factionName.equals(nearbyFaction)) continue;

            applyAttackSpeed(nearby, atkSpd);
            send(nearby, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.faction-buffed", "")
                    .replace("{player}", activator.getName()));

            Bukkit.getScheduler().runTaskLater(plugin, () -> removeAttackSpeed(nearby), duration * 20L);
        }
    }

    private String getFactionOf(Player player) {
        var fp = Bukkit.getPluginManager().getPlugin("FactionPlugin");
        if (fp == null) return null;
        try {
            Object api = fp.getClass().getMethod("getFactionAPI").invoke(fp);
            Object result = api.getClass().getMethod("getFactionName", UUID.class).invoke(api, player.getUniqueId());
            return result != null ? result.toString() : null;
        } catch (Exception e) { return null; }
    }

    public void cleanup(Player player) {
        Integer taskId = activeTasks.remove(player.getUniqueId());
        if (taskId != null) Bukkit.getScheduler().cancelTask(taskId);
        removeAttackSpeed(player);
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }
}