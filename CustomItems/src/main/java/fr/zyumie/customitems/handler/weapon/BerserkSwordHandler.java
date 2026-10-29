package fr.zyumie.customitems.handler.weapon;

import fr.zyumie.customitems.CustomItems;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
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

        // 1. Applique les buffs au joueur qui active
        applyBerserkEffects(player, duration);

        // 2. Buff des membres de faction proches
        buffFactionMembers(player, duration);

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
        removeBerserkEffects(player);
        send(player, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.ended", ""));
        activeTasks.remove(player.getUniqueId());
    }

    private void applyBerserkEffects(Player player, int durationSeconds) {
        int ticks = durationSeconds * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 0, false, false, true));
    }

    private void removeBerserkEffects(Player player) {
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    private void buffFactionMembers(Player activator, int durationSeconds) {
        double radius = plugin.getWeaponConfig().getDouble("weapons.berserk-sword.faction-buff-radius", 10.0);
        String factionName = getFactionOf(activator);

        // Debug optionnel pour voir si la faction est bien détectée pour l'activateur
        // plugin.getLogger().info("Faction de l'activateur (" + activator.getName() + ") : " + factionName);

        for (Player nearby : activator.getWorld().getNearbyEntitiesByType(Player.class, activator.getLocation(), radius)) {
            if (nearby.equals(activator)) continue;

            String nearbyFaction = getFactionOf(nearby);

            // Si l'activateur n'a pas de faction ou si les factions ne correspondent pas, on ignore
            if (factionName == null || !factionName.equals(nearbyFaction)) continue;

            applyBerserkEffects(nearby, durationSeconds);
            send(nearby, plugin.getWeaponConfig().getString("weapons.berserk-sword.messages.faction-buffed", "")
                    .replace("{player}", activator.getName()));
        }
    }

    private String getFactionOf(Player player) {
        // Teste plusieurs noms de plugins de factions courants pour éviter les erreurs de correspondance
        Plugin fp = Bukkit.getPluginManager().getPlugin("Faction");
        if (fp == null) fp = Bukkit.getPluginManager().getPlugin("Factions");
        if (fp == null) fp = Bukkit.getPluginManager().getPlugin("FactionPlugin");
        if (fp == null) fp = Bukkit.getPluginManager().getPlugin("FactionsUUID");

        if (fp == null) return null;

        try {
            Object api = fp.getClass().getMethod("getFactionAPI").invoke(fp);
            Object result = api.getClass().getMethod("getFactionName", UUID.class).invoke(api, player.getUniqueId());
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            // Affiche l'erreur exacte dans la console si la méthode de réflexion change
            plugin.getLogger().warning("Erreur getFactionOf pour " + player.getName() + " : " + e.getMessage());
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