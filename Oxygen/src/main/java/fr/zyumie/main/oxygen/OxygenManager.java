package fr.zyumie.main.oxygen;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import fr.zyumie.main.oxygen.api.OxygenSupplier;
import fr.zyumie.main.oxygen.api.events.OxygenChangeEvent;
import fr.zyumie.main.oxygen.api.events.PlayerOxygenDepletedEvent;
import fr.zyumie.main.oxygen.data.OxygenData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OxygenManager {

    private static final String NAMESPACE = "oxygen";

    private final Oxygen plugin;
    private final Map<UUID, OxygenData> cache = new ConcurrentHashMap<>();
    private final List<OxygenSupplier> suppliers = new ArrayList<>();

    public OxygenManager(Oxygen plugin) {
        this.plugin = plugin;
    }

    // ---------- Gestion des suppliers externes (armure, etc.) ----------

    public void registerSupplier(OxygenSupplier supplier) {
        suppliers.add(supplier);
        suppliers.sort(Comparator.comparingInt(OxygenSupplier::priority));
        plugin.getLogger().info("OxygenSupplier enregistré: " + supplier.getClass().getSimpleName());
    }

    public void unregisterSupplier(OxygenSupplier supplier) {
        suppliers.remove(supplier);
    }

    // ---------- Chargement / sauvegarde ----------

    public void loadPlayer(Player player) {
        DataStore store = MainAPI.get().getDataStore();
        Map<String, Object> raw = store.load(NAMESPACE, player.getUniqueId());
        double startingPercent = plugin.getConfig().getDouble("oxygen.starting-percent", 100.0);

        OxygenData data;
        if (raw == null || raw.isEmpty()) {
            data = new OxygenData(startingPercent);
        } else {
            Object percentObj = raw.get("percent");
            double percent = percentObj instanceof Number n ? n.doubleValue() : startingPercent;
            data = new OxygenData(percent);
        }
        cache.put(player.getUniqueId(), data);
    }

    public void savePlayer(Player player) {
        OxygenData data = cache.get(player.getUniqueId());
        if (data == null) return;
        DataStore store = MainAPI.get().getDataStore();
        store.save(NAMESPACE, player.getUniqueId(), Map.of("percent", data.getPercent()));
    }

    public void unloadPlayer(Player player) {
        savePlayer(player);
        cache.remove(player.getUniqueId());
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) savePlayer(p);
        }
    }

    // ---------- Accès / mutation de la jauge ----------

    public OxygenData getData(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(),
                u -> new OxygenData(plugin.getConfig().getDouble("oxygen.starting-percent", 100.0)));
    }

    public double getPercent(Player player) {
        return getData(player).getPercent();
    }

    public void setPercent(Player player, double newValue) {
        OxygenData data = getData(player);
        double old = data.getPercent();
        double clamped = Math.max(0.0, Math.min(100.0, newValue));
        if (old == clamped) return;

        data.setPercent(clamped);

        OxygenChangeEvent event = new OxygenChangeEvent(player, old, clamped);
        Bukkit.getPluginManager().callEvent(event);

        if (old > 0.0 && clamped <= 0.0 && !data.isSuffocating()) {
            data.setSuffocating(true);
            Bukkit.getPluginManager().callEvent(new PlayerOxygenDepletedEvent(player));
        } else if (clamped > 0.0 && data.isSuffocating()) {
            data.setSuffocating(false);
            String msg = plugin.getConfig().getString("messages.oxygen-restored", "");
            if (msg != null && !msg.isEmpty()) {
                player.sendMessage(fr.zyumie.main.oxygen.util.ChatUtil.color(
                        plugin.getConfig().getString("messages.prefix", "") + msg));
            }
        }
    }

    public void modify(Player player, double delta) {
        setPercent(player, getPercent(player) + delta);
    }

    // ---------- Logique de surface / sous-terre ----------

    public boolean isSurface(Player player) {
        int threshold = plugin.getConfig().getInt("oxygen.surface-y-threshold", 60);
        return player.getLocation().getBlockY() >= threshold;
    }

    /**
     * Tente de faire fournir le tick par un supplier externe (armure, etc.).
     * @return true si un supplier a couvert le besoin (poumons intacts ce tick)
     */
    public boolean trySupplyExternally(Player player, double percentNeeded) {
        for (OxygenSupplier supplier : suppliers) {
            if (supplier.supplyTick(player, percentNeeded)) {
                return true;
            }
        }
        return false;
    }

    public List<OxygenSupplier> getSuppliers() {
        return suppliers;
    }
}
