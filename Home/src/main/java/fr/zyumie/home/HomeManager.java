package fr.zyumie.home;

import fr.zyumie.home.model.HomeData;
import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {

    private static final String NAMESPACE = "home";
    private static final String LEGACY_DEFAULT_NAME = "default";

    private final Home plugin;
    // UUID -> (Nom du home en minuscules -> HomeData)
    private final Map<UUID, Map<String, HomeData>> homes = new ConcurrentHashMap<>();

    // Gestion des téléportations en cours (UUID -> BukkitTask)
    private final Map<UUID, BukkitTask> teleportTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> teleportLocations = new ConcurrentHashMap<>();

    // Fichier home.yml global
    private final File legacyFile;
    private YamlConfiguration legacyConfig;

    public HomeManager(Home plugin) {
        this.plugin = plugin;

        // Chemin direct vers le fichier home.yml dans le dossier plugins/Main/data/
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File mainDataDir = new File(pluginsDir, "Main/data");
        this.legacyFile = new File(mainDataDir, "home.yml");

        plugin.getLogger().info("[Home] Recherche de home.yml à l'emplacement : " + legacyFile.getAbsolutePath());
        loadLegacyFile();
    }

    // ---------- Gestion du fichier home.yml global ----------

    private void loadLegacyFile() {
        if (!legacyFile.exists()) {
            plugin.getLogger().info("[debug] Aucun home.yml trouvé à : " + legacyFile.getAbsolutePath());
            return;
        }
        legacyConfig = YamlConfiguration.loadConfiguration(legacyFile);
        int total = 0;
        ConfigurationSection nested = legacyConfig.getConfigurationSection("homes");
        if (nested != null) total += nested.getKeys(false).size();
        for (String k : legacyConfig.getKeys(false)) {
            if (!k.equals("homes")) total++;
        }
        plugin.getLogger().info("home.yml détecté : " + total + " entrée(s) prêtes à être synchronisées.");
    }

    private void saveLegacyFile() {
        try {
            legacyConfig.save(legacyFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder home.yml : " + ex.getMessage());
        }
    }

    private Location parseLocation(ConfigurationSection s) {
        World world = Bukkit.getWorld(s.getString("world", "world"));
        if (world == null) return null;
        return new Location(world,
                s.getDouble("x", 0),
                s.getDouble("y", 64),
                s.getDouble("z", 0),
                (float) s.getDouble("yaw", 0),
                (float) s.getDouble("pitch", 0));
    }

    private Map<String, HomeData> migrateLegacy(UUID uuid) {
        if (legacyConfig == null) return null;
        String key = uuid.toString();

        ConfigurationSection nested = legacyConfig.getConfigurationSection("homes");
        ConfigurationSection nestedSec = nested != null ? nested.getConfigurationSection(key) : null;
        ConfigurationSection rootSec = legacyConfig.getConfigurationSection(key);

        if (nestedSec == null && rootSec == null) return null;

        Map<String, HomeData> result = new ConcurrentHashMap<>();
        boolean allResolved = true;
        if (nestedSec != null) allResolved &= readLegacySection(uuid, nestedSec, result);
        if (rootSec != null) allResolved &= readLegacySection(uuid, rootSec, result);

        if (result.isEmpty()) return null;

        if (allResolved) {
            if (nestedSec != null) nested.set(key, null);
            if (rootSec != null) legacyConfig.set(key, null);
            saveLegacyFile();
        }
        return result;
    }

    private boolean hasLegacyEntry(UUID uuid) {
        if (legacyConfig == null) return false;
        String key = uuid.toString();
        ConfigurationSection nested = legacyConfig.getConfigurationSection("homes");
        return legacyConfig.isConfigurationSection(key)
                || (nested != null && nested.isConfigurationSection(key));
    }

    private boolean readLegacySection(UUID uuid, ConfigurationSection sec, Map<String, HomeData> out) {
        boolean ok = true;

        ConfigurationSection sub = sec.getConfigurationSection("homes");
        if (sub != null) {
            for (String name : sub.getKeys(false)) {
                ConfigurationSection h = sub.getConfigurationSection(name);
                if (h == null) continue;
                Location loc = parseLocation(h);
                if (loc == null) {
                    ok = false;
                    plugin.getLogger().warning("Monde introuvable '" + h.getString("world") + "' pour " + uuid + " (home " + name + ") : conservé dans home.yml.");
                    continue;
                }
                out.put(name.toLowerCase(), new HomeData(loc, h.getLong("set-time", System.currentTimeMillis())));
            }
        }

        if (sec.contains("world")) {
            Location loc = parseLocation(sec);
            if (loc == null) {
                ok = false;
                plugin.getLogger().warning("Monde introuvable '" + sec.getString("world") + "' pour " + uuid + " : conservé dans home.yml.");
            } else {
                out.putIfAbsent(LEGACY_DEFAULT_NAME, new HomeData(loc, sec.getLong("set-time", System.currentTimeMillis())));
            }
        }
        return ok;
    }

    // ---------- Chargement individuel (Priorité home.yml) ----------

    public void loadPlayer(UUID uuid) {
        if (homes.containsKey(uuid)) return;

        Map<String, HomeData> playerHomes = new ConcurrentHashMap<>();

        // 1. PRIORITÉ ABSOLUE : On regarde d'abord dans le fichier home.yml global
        if (legacyConfig != null && hasLegacyEntry(uuid)) {
            Map<String, HomeData> migrated = migrateLegacy(uuid);
            if (migrated != null && !migrated.isEmpty()) {
                playerHomes.putAll(migrated);
                homes.put(uuid, playerHomes);
                savePlayer(uuid); // Sauvegarde synchro dans le DataStore pour les prochaines fois
                plugin.getLogger().info("[Home] " + migrated.size() + " home(s) chargés et synchronisés depuis home.yml pour " + uuid + ".");
                return;
            }
        }

        // 2. SI ABSENT de home.yml, on interroge le DataStore classique
        DataStore store = MainAPI.get().getDataStore();
        Map<String, Object> raw = store.load(NAMESPACE, uuid.toString());

        if (raw != null && raw.get("homes") instanceof Map<?, ?> homesMap) {
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
                        long setTime = hData.get("set-time") instanceof Number n ? n.longValue() : System.currentTimeMillis();
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
        savePlayer(uuid);
    }

    public void deleteHome(UUID uuid, String name) {
        Map<String, HomeData> playerHomes = homes.get(uuid);
        if (playerHomes != null) {
            playerHomes.remove(name.toLowerCase());
            if (playerHomes.isEmpty()) {
                homes.remove(uuid);
            }
            savePlayer(uuid);
        }
    }

    public int getHomeCount(UUID uuid) {
        Map<String, HomeData> playerHomes = homes.get(uuid);
        return playerHomes != null ? playerHomes.size() : 0;
    }

    public int getMaxHomes(Player player) {
        for (int i = 5; i >= 1; i--) {
            if (player.hasPermission("home.max." + i)) {
                return i;
            }
        }
        return 1;
    }

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