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
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Épée Céleste — version anti-duplication (v2).
 *
 * - L'épée est transformée EN PLACE en masse (l'épée d'origine est embarquée dans le PDC de la masse).
 * - Chaque épée possède un UID unique : toute épée en double (même UID) est supprimée.
 * - Chaque masse possède un token à usage unique : une masse copiée ne donne jamais une 2e épée.
 * - Inventaire verrouillé pendant toute la durée du vol, déverrouillé dès la fin de l'utilisation de la masse.
 * - Les effets (propulsion, one-shot) ne s'appliquent que sur les JOUEURS.
 */
public class CelestialSwordHandler implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CustomItems plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey tokenKey;
    private final NamespacedKey swordDataKey;
    private final NamespacedKey uidKey;

    private final Map<UUID, Session> inFlight = new HashMap<>();

    /** Tokens déjà convertis en épée : une masse copiée ne peut jamais donner une 2e épée. */
    private final Set<UUID> consumedTokens = Collections.newSetFromMap(new LinkedHashMap<UUID, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > 10_000;
        }
    });

    private static final class Session {
        final UUID token = UUID.randomUUID();
        final int slot;              // slot où se trouvait l'épée au moment du kill
        UUID swordUid;               // UID de l'épée actuellement enfermée dans la masse
        long launchTime;
        long generation;
        boolean maceEquipped;

        Session(int slot) { this.slot = slot; }
    }

    public CelestialSwordHandler(CustomItems plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "celestial_owner");
        this.tokenKey = new NamespacedKey(plugin, "celestial_token");
        this.swordDataKey = new NamespacedKey(plugin, "celestial_sword_data");
        this.uidKey = new NamespacedKey(plugin, "celestial_uid");
        startFlightTickTask();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Vol
    // ══════════════════════════════════════════════════════════════════════

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
                    Session session = inFlight.get(uuid);
                    if (session == null) continue;

                    if (now - session.launchTime > 300) {
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
        UUID uuid = player.getUniqueId();
        Session session = inFlight.get(uuid);
        if (session == null) {
            session = new Session(player.getInventory().getHeldItemSlot());
            inFlight.put(uuid, session);
        }
        final Session current = session;
        current.launchTime = System.currentTimeMillis();
        final long gen = ++current.generation;

        double velocity = plugin.getWeaponConfig().getDouble("weapons.celestial-sword.launch-velocity", 2.5);
        Vector v = player.getVelocity();
        v.setY(velocity);
        player.setVelocity(v);
        player.setFallDistance(-9999);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f);
        send(player, plugin.getWeaponConfig().getString("weapons.celestial-sword.messages.launch", ""));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (inFlight.get(uuid) == current && current.generation == gen && player.isOnline()) {
                if (equipMace(player, current)) {
                    send(player, plugin.getWeaponConfig().getString("weapons.celestial-sword.messages.mace-form", ""));
                }
            }
        }, 10L);

        // Sécurité : fin de vol forcée après 30 s → l'inventaire est déverrouillé
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (inFlight.get(uuid) == current && current.generation == gen && player.isOnline()) {
                onFallLanding(player);
            }
        }, 600L);
    }

    /** Fin du vol : le verrou saute immédiatement, la masse redevient l'épée d'origine. */
    public void onFallLanding(Player player) {
        if (inFlight.remove(player.getUniqueId()) == null) return;
        sanitize(player.getInventory(), null); // masse -> épée d'origine, dans son slot
        send(player, plugin.getWeaponConfig().getString("weapons.celestial-sword.messages.miss-landing", ""));
    }

    /** Appelé uniquement pour un kill de JOUEUR (voir KillListener). */
    public void onKillWithSword(Player player) {
        launchPlayer(player);
    }

    /** Fin de vol forcée (mort, déco, arrêt serveur) : l'épée d'origine est restituée. */
    public void removeFromFlight(Player player) {
        if (inFlight.remove(player.getUniqueId()) != null) {
            sanitize(player.getInventory(), null);
        }
    }

    public boolean isInFlight(Player player) {
        return inFlight.containsKey(player.getUniqueId());
    }

    /**
     * Boss immunisés (clés PDC listées dans Weapon.yml → weapons.celestial-sword.boss-immunity-keys).
     */
    public boolean isBossImmune(Entity entity) {
        if (entity == null) return false;
        var cfg = plugin.getWeaponConfig();
        List<String> keys = cfg.contains("weapons.celestial-sword.boss-immunity-keys")
                ? cfg.getStringList("weapons.celestial-sword.boss-immunity-keys")
                : List.of("endboss:is_endboss");
        var pdc = entity.getPersistentDataContainer();
        for (String raw : keys) {
            NamespacedKey key = NamespacedKey.fromString(raw);
            if (key == null) continue;
            if (pdc.has(key, PersistentDataType.BYTE)
                    || pdc.has(key, PersistentDataType.STRING)
                    || pdc.has(key, PersistentDataType.INTEGER)) return true;
        }
        return false;
    }

    public void onHitWithMace(EntityDamageByEntityEvent event, Player attacker, LivingEntity target) {
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!isValidMace(attacker, held)) {
            // Masse illégitime (copie, reliquat de l'ancien bug…) : purge, aucun effet spécial
            if (isMace(held)) sanitize(attacker.getInventory(), attacker);
            return;
        }
        // Effets réservés aux joueurs : sur un mob → dégâts vanilla, pas de one-shot, pas de propulsion
        if (!(target instanceof Player pTarget)) return;
        if (pTarget.equals(attacker)) return;
        if (isBossImmune(target)) return;

        event.setCancelled(true);
        pTarget.setHealth(0);

        send(attacker, plugin.getWeaponConfig().getString("weapons.celestial-sword.messages.kill-slam", ""));
        launchPlayer(attacker); // la session continue : l'inventaire reste verrouillé jusqu'à l'atterrissage
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UID des épées
    // ══════════════════════════════════════════════════════════════════════

    /** Pose un UID unique sur l'épée s'il n'en a pas. Retourne l'UID. À appeler à la création. */
    public UUID stampUid(ItemStack sword) {
        UUID existing = uidOf(sword);
        if (existing != null) return existing;
        UUID uid = UUID.randomUUID();
        ItemMeta meta = sword.getItemMeta();
        meta.getPersistentDataContainer().set(uidKey, PersistentDataType.STRING, uid.toString());
        sword.setItemMeta(meta);
        return uid;
    }

    private UUID uidOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(uidKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }

    private boolean isUidInFlight(UUID uid) {
        for (Session s : inFlight.values()) {
            if (uid.equals(s.swordUid)) return true;
        }
        return false;
    }

    private boolean inventoryHasUid(Inventory inv, UUID uid) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isSword(it) && uid.equals(uidOf(it))) return true;
        }
        return false;
    }

    /**
     * Dédoublonne les épées sur l'ensemble des inventaires donnés (scope partagé) :
     * - épée sans UID → UID attribué
     * - UID déjà vu, ou UID actuellement enfermé dans une masse en vol → épée supprimée
     * Les inventaires sont traités dans l'ordre : le premier garde l'épée.
     */
    private void dedupe(String who, Inventory... invs) {
        Set<UUID> seen = new HashSet<>();
        for (Inventory inv : invs) {
            if (inv == null) continue;
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (!isSword(it)) continue;
                UUID uid = uidOf(it);
                if (uid == null) {
                    ItemStack copy = it.clone();
                    uid = stampUid(copy);
                    inv.setItem(i, copy);
                    seen.add(uid);
                    continue;
                }
                if (isUidInFlight(uid) || !seen.add(uid)) {
                    inv.setItem(i, null);
                    plugin.getLogger().warning("[Celestial] Épée dupliquée supprimée (UID " + uid + ") — " + who);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Transformation épée <-> masse (en place, sans création d'item)
    // ══════════════════════════════════════════════════════════════════════

    private boolean equipMace(Player player, Session session) {
        if (session.maceEquipped) return false;

        ItemStack sword = player.getInventory().getItem(session.slot);
        if (!isSword(sword)) return false; // pas d'épée à cet endroit → on ne crée RIEN

        ItemStack swordCopy = sword.clone();
        UUID uid = stampUid(swordCopy);

        ItemStack mace = plugin.getItemRegistry().buildCelestialMace();
        if (mace == null) return false;

        ItemMeta meta = mace.getItemMeta();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(tokenKey, PersistentDataType.STRING, session.token.toString());
        pdc.set(swordDataKey, PersistentDataType.BYTE_ARRAY, swordCopy.serializeAsBytes());
        mace.setItemMeta(meta);

        session.swordUid = uid;
        player.getInventory().setItem(session.slot, mace);
        session.maceEquipped = true;
        return true;
    }

    /**
     * Convertit une masse en l'épée d'origine. Retourne null si la masse est illégitime
     * (token inconnu, déjà converti = copie) : l'appelant doit alors la supprimer.
     */
    private ItemStack consumeMace(ItemStack mace) {
        UUID token = tokenOf(mace);
        if (token == null || !consumedTokens.add(token)) return null;

        ItemStack sword = null;
        byte[] data = mace.getItemMeta().getPersistentDataContainer()
                .get(swordDataKey, PersistentDataType.BYTE_ARRAY);
        if (data != null) {
            try { sword = ItemStack.deserializeBytes(data); } catch (Exception ignored) {}
        }
        if (sword == null) sword = plugin.getItemRegistry().buildCelestialSword(); // 1 masse -> 1 épée
        if (sword != null) stampUid(sword);
        return sword;
    }

    /** Remplace toute masse non légitime de l'inventaire par son épée (ou la supprime). */
    private void sanitize(Inventory inv, Player holder) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!isMace(it)) continue;
            if (holder != null && isValidMace(holder, it)) continue;
            inv.setItem(i, consumeMace(it));
        }
    }

    private void sanitizeGroundItem(Item entity) {
        ItemStack sword = consumeMace(entity.getItemStack());
        if (sword == null) entity.remove();
        else entity.setItemStack(sword);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Verrous pendant le vol (actifs de la propulsion jusqu'à l'atterrissage)
    // ══════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player) {
            if (inFlight.containsKey(player.getUniqueId())) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!inFlight.containsKey(player.getUniqueId())) return;
        if (!isCelestial(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        send(player, "<red>Impossible de lâcher cet objet en plein vol !");
    }

    /** Pendant le vol : AUCUN clic d'inventaire (couvre touches 1-9, F, shift-click, double-clic…). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && inFlight.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && inFlight.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (inFlight.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        // Vol terminé → on nettoie masses perdues et épées en double (inventaire + conteneur ouvert + ender chest)
        sanitize(event.getInventory(), null);
        sanitize(player.getInventory(), player);
        dedupe(player.getName(), player.getInventory(), event.getInventory(), player.getEnderChest());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!inFlight.containsKey(player.getUniqueId())) return;
        if (isCelestial(event.getMainHandItem()) || isCelestial(event.getOffHandItem())) event.setCancelled(true);
    }

    /** Item frames, armor stands, allays… */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!inFlight.containsKey(player.getUniqueId())) return;
        if (isCelestial(player.getInventory().getItemInMainHand())
                || isCelestial(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    /** Decorated pots, etc. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!inFlight.containsKey(player.getUniqueId())) return;
        if (isCelestial(player.getInventory().getItemInMainHand())
                || isCelestial(player.getInventory().getItemInOffHand())) {
            event.setCancelled(true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Fin de session : déco / mort / join / items au sol
    // ══════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeFromFlight(event.getPlayer());
    }

    /** Récupération après crash + attribution d'UID + dédoublonnage inventaire / ender chest. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        sanitize(player.getInventory(), null);
        sanitize(player.getEnderChest(), null);
        dedupe(player.getName(), player.getInventory(), player.getEnderChest());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        inFlight.remove(dead.getUniqueId());

        if (event.getKeepInventory()) {
            sanitize(dead.getInventory(), null);
            return;
        }
        ListIterator<ItemStack> it = event.getDrops().listIterator();
        while (it.hasNext()) {
            ItemStack drop = it.next();
            if (!isMace(drop)) continue;
            ItemStack sword = consumeMace(drop);
            if (sword == null) it.remove();
            else it.set(sword);
        }
    }

    /** Masse au sol → reconvertie/supprimée. Épée au sol → UID attribué, supprimée si UID en vol. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack stack = event.getEntity().getItemStack();

        if (isMace(stack)) {
            ItemStack sword = consumeMace(stack);
            if (sword == null) event.setCancelled(true);
            else event.getEntity().setItemStack(sword);
            return;
        }

        if (isSword(stack)) {
            UUID uid = uidOf(stack);
            if (uid != null && isUidInFlight(uid)) {
                event.setCancelled(true);
                plugin.getLogger().warning("[Celestial] Épée dupliquée au sol supprimée (UID " + uid + ")");
                return;
            }
            if (uid == null) {
                ItemStack copy = stack.clone();
                stampUid(copy);
                event.getEntity().setItemStack(copy);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();

        if (isMace(stack)) {
            event.setCancelled(true);
            sanitizeGroundItem(event.getItem());
            return;
        }

        if (isSword(stack) && event.getEntity() instanceof Player player) {
            UUID uid = uidOf(stack);
            if (uid == null) {
                ItemStack copy = stack.clone();
                stampUid(copy);
                event.getItem().setItemStack(copy);
                return;
            }
            if (isUidInFlight(uid) || inventoryHasUid(player.getInventory(), uid)
                    || inventoryHasUid(player.getEnderChest(), uid)) {
                event.setCancelled(true);
                event.getItem().remove();
                plugin.getLogger().warning("[Celestial] Épée dupliquée supprimée au ramassage (UID "
                        + uid + ") — " + player.getName());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (!isMace(event.getItem().getItemStack())) return;
        event.setCancelled(true);
        sanitizeGroundItem(event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (isMace(event.getItem())) event.setCancelled(true);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════

    private boolean isMace(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return ItemBuilder.isCustomItem(plugin, item, ItemRegistry.CELESTIAL_MACE);
    }

    private boolean isSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return ItemBuilder.isCustomItem(plugin, item, ItemRegistry.CELESTIAL_SWORD);
    }

    private boolean isCelestial(ItemStack item) {
        return isMace(item) || isSword(item);
    }

    private UUID tokenOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException e) { return null; }
    }

    /** Masse légitime = celle du vol en cours de CE joueur (token + propriétaire). */
    private boolean isValidMace(Player player, ItemStack item) {
        Session session = inFlight.get(player.getUniqueId());
        if (session == null || !isMace(item)) return false;
        if (!session.token.equals(tokenOf(item))) return false;
        String owner = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return player.getUniqueId().toString().equals(owner);
    }

    private void send(Player p, String message) {
        if (message == null || message.isEmpty()) return;
        p.sendMessage(MM.deserialize(message));
    }
}