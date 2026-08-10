package fr.zyumie.customitems.handler.weapon;

import fr.zyumie.customitems.CustomItems;
import fr.zyumie.customitems.ItemRegistry;
import fr.zyumie.customitems.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Épée/Masse Céleste.
 *
 * Correctifs anti-duplication :
 *  1. PlayerDropItemEvent → annulé si le joueur est en vol avec la mace
 *  2. La mace est taguée PDC "celestial_owner" = UUID du joueur
 *     → on retrouve et supprime EXACTEMENT sa mace au sol avant tout reset
 *  3. clearOwnedMaces() appelé dans onFallLanding() ET dans le timer de sécurité
 */
public class CelestialSwordHandler implements Listener {

    /** Clé PDC stockée dans la mace pour lier l'item à son propriétaire */
    private static final String OWNER_KEY = "celestial_owner";

    private final CustomItems plugin;
    private final Set<UUID> inFlight = new HashSet<>();

    public CelestialSwordHandler(CustomItems plugin) {
        this.plugin = plugin;
    }

    // ── Events ────────────────────────────────────────────────────────────

    /** Kill avec l'épée → propulsion */
    public void onKillWithSword(Player player) {
        launchPlayer(player);
    }

    /** Slam en chute avec la mace → one-shot + re-propulsion */
    public void onHitWithMace(EntityDamageByEntityEvent event, Player attacker, LivingEntity target) {
        if (!inFlight.contains(attacker.getUniqueId())) return;
        if (!ItemBuilder.isCustomItem(plugin, attacker.getInventory().getItemInMainHand(),
                ItemRegistry.CELESTIAL_MACE)) return;

        event.setCancelled(true);

        if (target instanceof Player pTarget) pTarget.setHealth(0);
        else target.damage(target.getMaxHealth() * 10);

        send(attacker, plugin.getConfig().getString(
                "weapons.celestial-sword.messages.kill-slam", ""));

        launchPlayer(attacker);
    }

    /** Atterrissage sans kill → 0 dégât + retour épée */
    public void onFallLanding(Player player) {
        if (!inFlight.contains(player.getUniqueId())) return;
        inFlight.remove(player.getUniqueId());
        clearOwnedMaces(player);          // ← supprime la mace si elle a été drop
        equipSword(player);
        send(player, plugin.getConfig().getString(
                "weapons.celestial-sword.messages.miss-landing", ""));
    }

    /**
     * FIX ANTI-DUPLICATION — Bloque le drop de la mace pendant le vol.
     * Sans ce handler, le joueur pouvait dropper la mace et recevoir quand
     * même l'épée via le timer de sécurité → la mace restait au sol.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!inFlight.contains(player.getUniqueId())) return;
        if (!ItemBuilder.isCustomItem(plugin, event.getItemDrop().getItemStack(),
                ItemRegistry.CELESTIAL_MACE)) return;

        event.setCancelled(true);
        send(player, "<red>Impossible de lâcher la masse en plein vol !");
    }

    // ── Logique ───────────────────────────────────────────────────────────

    private void launchPlayer(Player player) {
        inFlight.add(player.getUniqueId());

        double velocity = plugin.getConfig().getDouble(
                "weapons.celestial-sword.launch-velocity", 2.5);
        Vector v = player.getVelocity();
        v.setY(velocity);
        player.setVelocity(v);
        player.setFallDistance(-9999);

        player.getWorld().playSound(
                player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f);
        send(player, plugin.getConfig().getString(
                "weapons.celestial-sword.messages.launch", ""));

        // 0.5s après → équipe la mace (avec tag d'ownership)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (inFlight.contains(player.getUniqueId())) {
                equipMace(player);
                send(player, plugin.getConfig().getString(
                        "weapons.celestial-sword.messages.mace-form", ""));
            }
        }, 10L);

        // Sécurité : reset au bout de 8s si aucun atterrissage détecté
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!inFlight.contains(player.getUniqueId())) return;
            inFlight.remove(player.getUniqueId());
            clearOwnedMaces(player);      // ← FIX : nettoie avant de redonner l'épée
            equipSword(player);
        }, 160L);
    }

    private void equipMace(Player player) {
        ItemStack mace = plugin.getItemRegistry().buildCelestialMace();
        if (mace == null) return;

        // Tag "celestial_owner" = UUID du joueur → retrouvable si drop
        var meta = mace.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, OWNER_KEY),
                PersistentDataType.STRING,
                player.getUniqueId().toString());
        mace.setItemMeta(meta);

        player.getInventory().setItemInMainHand(mace);
    }

    private void equipSword(Player player) {
        ItemStack sword = plugin.getItemRegistry().buildCelestialSword();
        if (sword != null) player.getInventory().setItemInMainHand(sword);
    }

    /**
     * Supprime toutes les entités Item de type "celestial_mace"
     * appartenant à ce joueur dans un rayon de 50 blocs.
     * On cible uniquement la mace du joueur grâce au tag PDC "celestial_owner".
     */
    private void clearOwnedMaces(Player player) {
        UUID ownerUuid = player.getUniqueId();
        NamespacedKey ownerKey = new NamespacedKey(plugin, OWNER_KEY);

        player.getWorld()
              .getNearbyEntitiesByType(Item.class, player.getLocation(), 50)
              .stream()
              .filter(itemEntity -> {
                  ItemStack stack = itemEntity.getItemStack();
                  if (!ItemBuilder.isCustomItem(plugin, stack, ItemRegistry.CELESTIAL_MACE))
                      return false;
                  // Vérifie que la mace appartient bien à CE joueur
                  if (!stack.hasItemMeta()) return false;
                  String storedUuid = stack.getItemMeta()
                          .getPersistentDataContainer()
                          .get(ownerKey, PersistentDataType.STRING);
                  return ownerUuid.toString().equals(storedUuid);
              })
              .forEach(Entity::remove);
    }

    // ── API publique ──────────────────────────────────────────────────────

    public boolean isInFlight(Player player) {
        return inFlight.contains(player.getUniqueId());
    }

    public void removeFromFlight(Player player) {
        if (!inFlight.remove(player.getUniqueId())) return;
        clearOwnedMaces(player); // nettoyage en cas de mort/déco
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void send(Player p, String legacy) {
        if (legacy == null || legacy.isEmpty()) return;
        p.sendMessage(MiniMessage.miniMessage().deserialize(
                legacy.replace("&e","<yellow>").replace("&6","<gold>")
                      .replace("&c","<red>").replace("&7","<gray>")
                      .replace("&l","<bold>")));
    }
}
