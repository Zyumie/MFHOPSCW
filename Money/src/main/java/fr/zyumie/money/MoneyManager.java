package fr.zyumie.money;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MoneyManager {

    private static final String NS = "money";

    private final Money plugin;
    // UUID → balance
    private final Map<UUID, Double> cache = new ConcurrentHashMap<>();

    public MoneyManager(Money plugin) {
        this.plugin = plugin;
    }

    // ── Persistence ──────────────────────────────────────────────────────

    public void loadPlayer(UUID uuid) {
        DataStore store = MainAPI.get().getDataStore();
        Map<String, Object> raw = store.load(NS, uuid.toString());
        double starting = plugin.getConfig().getDouble("economy.starting-balance", 500.0);
        if (raw != null && raw.get("balance") instanceof Number n) {
            cache.put(uuid, n.doubleValue());
        } else {
            cache.put(uuid, starting);
            save(uuid);
        }
    }

    public void save(UUID uuid) {
        Double bal = cache.get(uuid);
        if (bal == null) return;
        MainAPI.get().getDataStore().save(NS, uuid.toString(), Map.of("balance", bal));
    }

    public void unload(UUID uuid) {
        save(uuid);
        cache.remove(uuid);
    }

    public void saveAll() {
        cache.keySet().forEach(this::save);
    }

    // ── Accès ────────────────────────────────────────────────────────────

    public double getBalance(UUID uuid) {
        if (!cache.containsKey(uuid)) loadPlayer(uuid); // chargement lazy pour offline players
        return cache.getOrDefault(uuid, 0.0);
    }

    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    public boolean deposit(UUID uuid, double amount) {
        double max = plugin.getConfig().getDouble("economy.max-balance", 999_999_999.0);
        double current = getBalance(uuid);
        if (current + amount > max) return false;
        cache.put(uuid, current + amount);
        save(uuid);
        return true;
    }

    public boolean withdraw(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current < amount) return false;
        cache.put(uuid, current - amount);
        save(uuid);
        return true;
    }

    public void set(UUID uuid, double amount) {
        double max = plugin.getConfig().getDouble("economy.max-balance", 999_999_999.0);
        cache.put(uuid, Math.max(0, Math.min(max, amount)));
        save(uuid);
    }

    // ── Classement ───────────────────────────────────────────────────────

    /**
     * Retourne les N entrées les plus riches (tous les joueurs connus, online ou offline).
     * Charge les données de tous les UUID connus depuis le DataStore.
     */
    public List<Map.Entry<String, Double>> getTopBalances(int limit) {
        DataStore store = MainAPI.get().getDataStore();
        Map<String, Double> all = new LinkedHashMap<>();

        // En cache (joueurs connectés)
        for (Map.Entry<UUID, Double> e : cache.entrySet()) {
            String name = nameOf(e.getKey());
            all.put(name, e.getValue());
        }

        // Sur disque (joueurs déconnectés pas en cache)
        for (String key : store.listKeys(NS)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (cache.containsKey(uuid)) continue; // déjà en cache
                Map<String, Object> raw = store.load(NS, key);
                if (raw != null && raw.get("balance") instanceof Number n) {
                    String name = nameOf(uuid);
                    all.put(name, n.doubleValue());
                }
            } catch (IllegalArgumentException ignored) {}
        }

        return all.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }
}
