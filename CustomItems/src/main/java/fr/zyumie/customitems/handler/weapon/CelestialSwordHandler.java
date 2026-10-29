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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CelestialSwordHandler implements Listener {

    private static final String OWNER_KEY = "celestial_owner";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CustomItems plugin;
    private final Map<UUID, Long> inFlight = new HashMap<>();

    public CelestialSwordHandler(CustomItems plugin) {
        this.plugin = plugin;
        startFlightTickTask();
    }

    private void startFlightTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (inFlight.isEmpty()) return;

                long now = System.currentTimeMillis();

                for (UUID uuid : new ArrayList<>(inFlight.keySet())) {
                    Player player = plugin.getServer().getPlayer(uuid);

                    if (player == null || !player.isOnline()) {
                        inFlight.remove(uuid);
                        continue;
                    }

                    Long launchTime = inFlight.get(uuid);
                    if (launchTime == null) continue;

                    if (now - launchTime > 300) {
                        boolean isOnGround = ((Entity) player).isOnGround();
                        boolean inLiquid = player.isSwimming() || player.getLocation().getBlock().isLiquid();
                        boolean hasBlockBelow = !player.getLocation().subtract(0, 0.1, 0).getBlock().isPassable();

                        if (isOnGround || inLiquid || hasBlockBelow) {
                            onFallLanding(player);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void launchPlayer(Player player) {
        long currentLaunchTime = System.currentTimeMillis();
        inFlight.put(player.getUniqueId(), currentLaunchTime);

        // Correction : getWeaponConfig()
        double velocity = plugin.getWeaponConfig().getDouble("weapons.celestial-sword.launch-velocity", 2.5);
        Vector v = player.getVelocity();
        v.setY(velocity);
        player.setVelocity(v);
        player.setFallDistance(-9999);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f);
        send(player, plugin.getWeaponConfig().getString("weapons.celestial-sword.messages.launch", ""));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Long activeSession = inFlight.get(player.getUniqueId());
            if (activeSession != null && activeSession == currentLaunchTime) {
                equipMace(player);
                send(player, plugin.getWeaponConfig().getString("weapons.celestial-sword.messages.mace-form", ""));
            }
        }, 10L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Long activeSession = inFlight.get(player.getUniqueId());
            if (activeSession != null && activeSession == currentLaunchTime) {
                onFallLanding(player);
            }
        }, 600L);
    }

    public void onFallLanding(Player player) {
        if (inFlight.remove(player.getUniqueId()) == null) return;

        clearOwnedMaces(player);
        equipSword(player);
        send(player, plugin.getWeaponConfig().getString("weapons.celestial-sword.messages.miss-landing", ""));
    }

    public void onKillWithSword(Player player) {
        launchPlayer(player);
    }

    public void onHitWithMace(EntityDamageByEntityEvent event, Player attacker, LivingEntity target) {
        if (!inFlight.containsKey(attacker.getUniqueId())) return;
        if (!isMace(attacker.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        if (target instanceof Player pTarget) pTarget.setHealth(0);
        else target.damage(target.getMaxHealth() * 10);

        send(attacker, plugin.getWeaponConfig().getString("weapons.celestial-sword.messages.kill-slam", ""));

        launchPlayer(attacker);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player) {
            if (inFlight.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeFromFlight(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!inFlight.containsKey(player.getUniqueId())) return;
        if (!isMace(event.getItemDrop().getItemStack())) return;

        event.setCancelled(true);
        send(player, "<red>Impossible de lâcher la masse en plein vol !");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !inFlight.containsKey(player.getUniqueId())) return;

        if (isMace(event.getCurrentItem()) || isMace(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !inFlight.containsKey(player.getUniqueId())) return;

        if (isMace(event.getOldCursor()) || isMace(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!inFlight.containsKey(player.getUniqueId())) return;

        if (isMace(event.getMainHandItem()) || isMace(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    private void equipMace(Player player) {
        ItemStack mace = plugin.getItemRegistry().buildCelestialMace();
        if (mace == null) return;

        var meta = mace.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, OWNER_KEY),
                PersistentDataType.STRING,
                player.getUniqueId().toString());
        mace.setItemMeta(meta);

        player.getInventory().setItemInMainHand(mace);
    }

    private void equipSword(Player player) {
        removeMacesFromInventory(player);
        ItemStack sword = plugin.getItemRegistry().buildCelestialSword();
        if (sword != null) {
            player.getInventory().setItemInMainHand(sword);
        }
    }

    private void removeMacesFromInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean hasChanged = false;

        for (int i = 0; i < contents.length; i++) {
            if (isMace(contents[i])) {
                contents[i] = null;
                hasChanged = true;
            }
        }

        if (hasChanged) {
            player.getInventory().setContents(contents);
        }
    }

    private void clearOwnedMaces(Player player) {
        UUID ownerUuid = player.getUniqueId();
        NamespacedKey ownerKey = new NamespacedKey(plugin, OWNER_KEY);

        player.getWorld().getNearbyEntitiesByType(Item.class, player.getLocation(), 20).stream()
                .filter(itemEntity -> {
                    ItemStack stack = itemEntity.getItemStack();
                    if (!isMace(stack)) return false;
                    String storedUuid = stack.getItemMeta()
                            .getPersistentDataContainer()
                            .get(ownerKey, PersistentDataType.STRING);
                    return ownerUuid.toString().equals(storedUuid);
                })
                .forEach(Entity::remove);
    }

    private boolean isMace(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return ItemBuilder.isCustomItem(plugin, item, ItemRegistry.CELESTIAL_MACE);
    }

    public boolean isInFlight(Player player) {
        return inFlight.containsKey(player.getUniqueId());
    }

    public void removeFromFlight(Player player) {
        if (inFlight.remove(player.getUniqueId()) != null) {
            clearOwnedMaces(player);
            removeMacesFromInventory(player);
        }
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MM.deserialize(message));
    }
}