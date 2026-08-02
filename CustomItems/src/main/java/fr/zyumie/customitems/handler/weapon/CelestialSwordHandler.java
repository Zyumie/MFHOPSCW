package fr.zyumie.customitems.handler.weapon;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée/Masse Céleste :
 * 1. Kill avec l'épée → propulsion +30 blocs + transformation en Masse
 * 2. En chute tenant la Masse + frappe joueur → one-shot + re-propulsion
 * 3. En chute + atterrissage sans kill → 0 dégât + retour Épée
 */
public class CelestialSwordHandler {

    private final CustomItems plugin;
    // Joueurs actuellement en vol céleste
    private final Set<UUID> inFlight = new HashSet<>();

    public CelestialSwordHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    /** Appelé quand un joueur tue une entité avec l'épée céleste */
    public void onKillWithSword(Player player) {
        launchPlayer(player);
    }

    /** Appelé dans EntityDamageByEntityEvent — vérifie si c'est un slam en chute */
    public void onHitWithMace(EntityDamageByEntityEvent event, Player attacker, LivingEntity target) {
        if (!inFlight.contains(attacker.getUniqueId())) return;
        if (!ItemBuilder.isCustomItem(plugin, attacker.getInventory().getItemInMainHand(),
                ItemRegistry.CELESTIAL_MACE)) return;

        event.setCancelled(true); // On gère nous-mêmes

        // One-shot la cible
        if (target instanceof Player pTarget) {
            pTarget.setHealth(0); // mort instantanée
        } else {
            target.damage(target.getMaxHealth() * 10); // tue les mobs aussi
        }

        send(attacker, plugin.getConfig().getString(
                "weapons.celestial-sword.messages.kill-slam", ""));

        // Re-propulsion
        launchPlayer(attacker);
    }

    /** Appelé quand le joueur touche le sol (EntityDamageEvent cause=FALL) */
    public void onFallLanding(Player player) {
        if (!inFlight.contains(player.getUniqueId())) return;
        inFlight.remove(player.getUniqueId());

        // Remplace la Masse par l'Épée
        equipSword(player);
        send(player, plugin.getConfig().getString(
                "weapons.celestial-sword.messages.miss-landing", ""));
    }

    // ── Internes ──────────────────────────────────────────────────────

    private void launchPlayer(Player player) {
        // Marque le joueur en vol
        inFlight.add(player.getUniqueId());

        // Propulsion verticale
        double velocity = plugin.getConfig().getDouble(
                "weapons.celestial-sword.launch-velocity", 2.5);
        Vector v = player.getVelocity();
        v.setY(velocity);
        player.setVelocity(v);
        player.setFallDistance(-9999); // annule les dégâts de chute

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f);
        send(player, plugin.getConfig().getString(
                "weapons.celestial-sword.messages.launch", ""));

        // Équipe la Masse après 0.5s (quand le joueur monte)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (inFlight.contains(player.getUniqueId())) {
                equipMace(player);
                send(player, plugin.getConfig().getString(
                        "weapons.celestial-sword.messages.mace-form", ""));
            }
        }, 10L);

        // Sécurité : si atterrissage non détecté après 8s, reset
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (inFlight.contains(player.getUniqueId())) {
                inFlight.remove(player.getUniqueId());
                equipSword(player);
            }
        }, 160L);
    }

    private void equipMace(Player player) {
        ItemStack mace = plugin.getItemRegistry().buildCelestialMace();
        if (mace != null) {
            player.getInventory().setItemInMainHand(mace);
        }
    }

    private void equipSword(Player player) {
        ItemStack sword = plugin.getItemRegistry().buildCelestialSword();
        if (sword != null) {
            player.getInventory().setItemInMainHand(sword);
        }
    }

    public boolean isInFlight(Player player) {
        return inFlight.contains(player.getUniqueId());
    }

    public void removeFromFlight(Player player) {
        inFlight.remove(player.getUniqueId());
    }

    private void send(Player p, String legacy) {
        if (legacy == null || legacy.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(
                legacy.replace("&e","<yellow>").replace("&6","<gold>")
                      .replace("&c","<red>").replace("&7","<gray>").replace("&l","<bold>")));
    }
}
