package fr.zyumie.home;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {

    private static final String NAMESPACE = "home";

    private final Home plugin;
    // UUID -> Home location (null si pas de home)
    private final Map<UUID, Location> homes = new ConcurrentHashMap<>();
    // UUID -> timestamp de la dernière mort (ms). -1 = jamais mort.
    private final Map<UUID, Long> deathTimestamps = new ConcurrentHashMap<>();
    // UUID -> true si le joueur a déjà eu au moins une mort (débloque /sethome)
    private final Map<UUID, Boolean> hasDied = new ConcurrentHashMap<>();

    public HomeManager(Home plugin) {
        this.plugin = plugin;
    }

    // ---------- Persistence ----------

    public void loadPlayer(UUID uuid) {
        DataStore store = MainAPI.get().getDataStore();
        Map<String, Object> raw = store.load(NAMESPACE, uuid.toString());
        if (raw == null) return;

        // Home location
        if (raw.containsKey("world")) {
            World world = Bukkit.getWorld(String.valueOf(raw.get("world")));
            if (world != null) {
                double x = raw.get("x") instanceof Number n ? n.doubleValue() : 0;
                double y = raw.get("y") instanceof Number n ? n.doubleValue() : 64;
                double z = raw.get("z") instanceof Number n ? n.doubleValue() : 0;
                homes.put(uuid, new Location(world, x, y, z));
            }
        }
        if (raw.containsKey("hasDied"))
            hasDied.put(uuid, Boolean.parseBoolean(String.valueOf(raw.get("hasDied"))));
        if (raw.containsKey("lastDeath"))
            deathTimestamps.put(uuid, ((Number) raw.get("lastDeath")).longValue());
    }

    public void savePlayer(UUID uuid) {
        Map<String, Object> data = new HashMap<>();
        Location home = homes.get(uuid);
        if (home != null && home.getWorld() != null) {
            data.put("world", home.getWorld().getName());
            data.put("x", home.getX());
            data.put("y", home.getY());
            data.put("z", home.getZ());
        }
        data.put("hasDied", hasDied.getOrDefault(uuid, false));
        data.put("lastDeath", deathTimestamps.getOrDefault(uuid, -1L));
        MainAPI.get().getDataStore().save(NAMESPACE, uuid.toString(), data);
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        homes.remove(uuid);
        deathTimestamps.remove(uuid);
        hasDied.remove(uuid);
    }

    // ---------- Home ----------

    public boolean hasHome(UUID uuid) { return homes.containsKey(uuid); }
    public Location getHome(UUID uuid) { return homes.get(uuid); }
    public void setHome(UUID uuid, Location loc) { homes.put(uuid, loc); }
    public void deleteHome(UUID uuid) { homes.remove(uuid); }

    // ---------- Mort / cooldown ----------

    public boolean hasDiedOnce(UUID uuid) { return hasDied.getOrDefault(uuid, false); }

    public void recordDeath(UUID uuid) {
        hasDied.put(uuid, true);
        deathTimestamps.put(uuid, System.currentTimeMillis());
    }

    /**
     * Secondes restantes du cooldown post-mort. 0 si cooldown expiré ou jamais mort.
     */
    public long getRemainingCooldownSeconds(UUID uuid) {
        long deathTime = deathTimestamps.getOrDefault(uuid, -1L);
        if (deathTime < 0) return 0;
        int cooldown = plugin.getConfig().getInt("home.death-cooldown-seconds", 300);
        long elapsed = (System.currentTimeMillis() - deathTime) / 1000;
        long remaining = cooldown - elapsed;
        return Math.max(0, remaining);
    }

    public boolean isCooldownActive(UUID uuid) {
        return getRemainingCooldownSeconds(uuid) > 0;
    }

    public void saveAll() {
        for (UUID uuid : homes.keySet()) savePlayer(uuid);
    }
}
