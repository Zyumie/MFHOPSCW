package fr.zyumie.home;

import fr.zyumie.home.model.HomeData;
import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {

    private static final String NAMESPACE = "home";

    private final Home plugin;
    // UUID -> (Nom du home en minuscules -> HomeData)
    private final Map<UUID, Map<String, HomeData>> homes = new ConcurrentHashMap<>();

    // Gestion des téléportations en cours (UUID -> BukkitTask)
    private final Map<UUID, BukkitTask> teleportTasks = new ConcurrentHashMap<>();
    // Stockage de la position initiale pour annuler si le joueur bouge
    private final Map<UUID, Location> teleportLocations = new ConcurrentHashMap<>();

    public HomeManager(Home plugin) {
        this.plugin = plugin;
    }

    // ---------- Persistence & Migration ----------

    public void loadPlayer(UUID uuid) {
        DataStore store = MainAPI.get().getDataStore();
        Map<String, Object> raw = store.load(NAMESPACE, uuid.toString());
        if (raw == null) return;

        Map<String, HomeData> playerHomes = new ConcurrentHashMap<>();

        // Migration rétrocompatible de l'ancien format (single home) vers le multi-home
        if (raw.containsKey("world")) {
            World world = Bukkit.getWorld(String.valueOf(raw.get("world")));
            if (world != null) {
                double x = getDouble(raw, "x", 0);
                double y = getDouble(raw, "y", 64);
                double z = getDouble(raw, "z", 0);
                float yaw = getFloat(raw, "yaw", 0f);
                float pitch = getFloat(raw, "pitch", 0f);
                long setTime = raw.containsKey("set-time") ? ((Number) raw.get("set-time")).longValue() : System.currentTimeMillis();
                playerHomes.put("default", new HomeData(new Location(world, x, y, z, yaw, pitch), setTime));
            }
        }

        // Chargement du nouveau format multi-homes
        if (raw.containsKey("homes") && raw.get("homes") instanceof Map<?, ?> homesMap) {
            for (Map.Entry<?, ?> entry : homesMap.entrySet()) {
                String homeName = String.valueOf(entry.getKey()).toLowerCase();
                if (entry.getValue() instanceof Map<?, ?> hData) {
                    World world = Bukkit.getWorld(String.valueOf(hData.get("world")));
                    if (world != null) {
                        double x = getDouble(hData, "x", 0);
                        double y = getDouble(hData, "y", 64);
                        double z = getDouble(hData, "z", 0);
                        float yaw = getFloat(hData, "yaw", 0f);
                        float pitch = getFloat(hData, "pitch", 0f);
                        long setTime = hData.containsKey("set-time") ? ((Number) hData.get("set-time")).longValue() : System.currentTimeMillis();
                        playerHomes.put(homeName, new HomeData(new Location(world, x, y, z, yaw, pitch), setTime));
                    }
                }
            }
        }

        if (!playerHomes.isEmpty()) {
            homes.put(uuid, playerHomes);
        }
    }

    public void savePlayer(UUID uuid) {
        Map<String, Object> data = new HashMap<>();
        Map<String, HomeData> playerHomes = homes.get(uuid);

        if (playerHomes != null && !playerHomes.isEmpty()) {
            Map<String, Object> homesMap = new HashMap<>();
            for (Map.Entry<String, HomeData> entry : playerHomes.entrySet()) {
                HomeData hd = entry.getValue();
                Location loc = hd.getLocation();
                if (loc.getWorld() != null) {
                    Map<String, Object> hMap = new HashMap<>();
                    hMap.put("world", loc.getWorld().getName());
                    hMap.put("x", loc.getX());
                    hMap.put("y", loc.getY());
                    hMap.put("z", loc.getZ());
                    hMap.put("yaw", loc.getYaw());
                    hMap.put("pitch", loc.getPitch());
                    hMap.put("set-time", hd.getSetTime());
                    homesMap.put(entry.getKey(), hMap);
                }
            }
            data.put("homes", homesMap);
        }

        MainAPI.get().getDataStore().save(NAMESPACE, uuid.toString(), data);
    }

    public void unloadPlayer(UUID uuid) {
        cancelTeleport(uuid);
        savePlayer(uuid);
        homes.remove(uuid);
    }

    // ---------- Gestion des Homes ----------

    public Map<String, HomeData> getHomes(UUID uuid) {
        return homes.getOrDefault(uuid, new HashMap<>());
    }

    public boolean hasHome(UUID uuid, String name) {
        Map<String, HomeData> playerHomes = homes.get(uuid);
        return playerHomes != null && playerHomes.containsKey(name.toLowerCase());
    }

    public Location getHome(UUID uuid, String name) {
        Map<String, HomeData> playerHomes = homes.get(uuid);
        if (playerHomes == null) return null;
        HomeData hd = playerHomes.get(name.toLowerCase());
        return hd != null ? hd.getLocation() : null;
    }

    public void setHome(UUID uuid, String name, Location loc) {
        homes.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(name.toLowerCase(), new HomeData(loc, System.currentTimeMillis()));
    }

    public void deleteHome(UUID uuid, String name) {
        Map<String, HomeData> playerHomes = homes.get(uuid);
        if (playerHomes != null) {
            playerHomes.remove(name.toLowerCase());
            if (playerHomes.isEmpty()) {
                homes.remove(uuid);
            }
        }
    }

    public int getHomeCount(UUID uuid) {
        Map<String, HomeData> playerHomes = homes.get(uuid);
        return playerHomes != null ? playerHomes.size() : 0;
    }

    // ---------- Permissions Max Homes (1 à 5) ----------

    public int getMaxHomes(Player player) {
        for (int i = 5; i >= 1; i--) {
            if (player.hasPermission("home.max." + i)) {
                return i;
            }
        }
        return 1; // Par défaut, 1 home maximum
    }

    // ---------- Gestion du Cooldown de Téléportation ----------

    public boolean isTeleporting(UUID uuid) {
        return teleportTasks.containsKey(uuid);
    }

    public void setTeleportTask(UUID uuid, BukkitTask task, Location loc) {
        cancelTeleport(uuid);
        teleportTasks.put(uuid, task);
        teleportLocations.put(uuid, loc);
    }

    public void cancelTeleport(UUID uuid) {
        BukkitTask task = teleportTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        teleportLocations.remove(uuid);
    }

    public boolean hasMoved(Player player) {
        Location initial = teleportLocations.get(player.getUniqueId());
        if (initial == null || initial.getWorld() != player.getWorld()) return true;
        return initial.distanceSquared(player.getLocation()) > 0.25; // Tolérance minime pour les micro-mouvements de tête
    }

    public void saveAll() {
        for (UUID uuid : homes.keySet()) {
            savePlayer(uuid);
        }
    }

    private double getDouble(Map<?, ?> map, String key, double def) {
        Object val = map.get(key);
        return val instanceof Number n ? n.doubleValue() : def;
    }

    private float getFloat(Map<?, ?> map, String key, float def) {
        Object val = map.get(key);
        return val instanceof Number n ? n.floatValue() : def;
    }
}