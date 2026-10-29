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

/**
 * Épée Berserk :
 * - Clic droit → Mode berserk 20s (vitesse d'attaque ×∞, style PvP 1.8) pour le joueur et sa faction
 * - Buff faction dans un rayon de 10 blocs
 * - Cooldown 3 minutes (180s)
 */
public class BerserkSwordHandler {

    private static final String MOD_NAME = "berserk_attack_speed";

    private final CustomItems plugin;
    // UUID → timestamp de fin de cooldown
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    // UUID → task id de fin de berserk
    private final Map<UUID, Integer> activeTasks = new HashMap<>();

    public BerserkSwordHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    public void activate(Player player) {
        if (!plugin.getWeaponConfig().getBoolean("weapons.berserk-sword.enabled", true)) return;

        // Vérifie si le joueur est déjà en train de subir l'effet (pendant la durée active)
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

        // 1. Applique la vitesse d'attaque extrême au JOUEUR qui active l'objet
        applyAttackSpeed(player, atkSpd);

        // 2. Buff des membres de faction proches (le joueur est exclu de cette boucle car déjà buffé juste au-dessus)
        buffFactionMembers(player);

        send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.activated", "")
                .replace("{duration}", String.valueOf(duration)));

        // Fin du berserk pour le joueur après N secondes
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

    // ── Vitesse d'attaque ─────────────────────────────────────────────

    private void applyAttackSpeed(Player player, double value) {
        var attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;

        NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
        attr.getModifiers().stream()
                .filter(m -> m.key().equals(key))
                .toList().forEach(attr::removeModifier);

        attr.addModifier(new AttributeModifier(key, value, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeAttackSpeed(Player player) {
        var attr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) return;
        NamespacedKey key = new NamespacedKey(plugin, MOD_NAME);
        attr.getModifiers().stream()
                .filter(m -> m.key().equals(key))
                .toList().forEach(attr::removeModifier);
    }

    // ── Buff faction ──────────────────────────────────────────────────

    private void buffFactionMembers(Player activator) {
        double radius = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.faction-buff-radius", 10.0);
        double atkSpd = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.attack-speed-value", 1024.0);
        int duration  = plugin.getWeaponConfig().getInt("weapons.berserk-sword.berserk-duration-seconds", 20);
        String factionName = getFactionOf(activator);

        for (Player nearby : activator.getWorld().getNearbyEntitiesByType(
                Player.class, activator.getLocation(), radius)) {
            if (nearby.equals(activator)) continue;
            // Vérifie que le joueur est dans la même faction
            String nearbyFaction = getFactionOf(nearby);
            if (factionName == null || !factionName.equals(nearbyFaction)) continue;

            applyAttackSpeed(nearby, atkSpd);
            send(nearby, plugin.getWeaponConfig().getString(
                            "weapons.berserk-sword.messages.faction-buffed", "")
                    .replace("{player}", activator.getName()));

            // Retire le buff au bout du même temps pour le membre de faction
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> removeAttackSpeed(nearby), duration * 20L);
        }
    }

    private String getFactionOf(Player player) {
        var fp = Bukkit.getPluginManager().getPlugin("FactionPlugin");
        if (fp == null) return null;
        try {
            Object api = fp.getClass().getMethod("getFactionAPI").invoke(fp);
            Object result = api.getClass().getMethod("getFactionName", UUID.class)
                    .invoke(api, player.getUniqueId());
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