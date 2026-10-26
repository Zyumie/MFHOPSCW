package fr.zyumie.money;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
            cache.put(uuid, sanitize(n.doubleValue()));
        } else {
            cache.put(uuid, starting);
            save(uuid);
        }
    }

    /**
     * Équivalent async de loadPlayer(), à utiliser sur les chemins "chauds" comme la connexion
     * d'un joueur (PlayerJoinEvent), pour ne pas bloquer le thread principal avec une lecture
     * disque à chaque connexion. La lecture se fait hors-thread ; le cache (partagé) n'est modifié
     * que depuis le thread principal, comme partout ailleurs dans cette classe.
     * getBalance() garde son filet de sécurité synchrone si ce chargement n'a pas eu le temps de finir.
     */
    public void loadPlayerAsync(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DataStore store = MainAPI.get().getDataStore();
            Map<String, Object> raw = store.load(NS, uuid.toString());
            double starting = plugin.getConfig().getDouble("economy.starting-balance", 500.0);
            boolean isNew = !(raw != null && raw.get("balance") instanceof Number);
            double value = isNew ? starting : sanitize(((Number) raw.get("balance")).doubleValue());

            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.put(uuid, value);
                if (isNew) save(uuid); // écrit le solde de départ pour un tout nouveau joueur
            });
        });
    }

    /** Un solde corrompu (NaN/Infinity/négatif) sur disque est ramené à 0 plutôt que propagé. */
    private static double sanitize(double v) {
        return Double.isFinite(v) && v >= 0 ? v : 0.0;
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

    /** Montant utilisable pour une transaction : fini (ni NaN ni ±Infinity) et positif ou nul. */
    public static boolean isValidAmount(double amount) {
        return Double.isFinite(amount) && amount >= 0;
    }

    public boolean has(UUID uuid, double amount) {
        if (!isValidAmount(amount)) return false;
        return getBalance(uuid) >= amount;
    }

    public boolean deposit(UUID uuid, double amount) {
        if (!isValidAmount(amount)) return false;
        double max = plugin.getConfig().getDouble("economy.max-balance", 999_999_999.0);
        double current = getBalance(uuid);
        if (current + amount > max) return false;
        cache.put(uuid, current + amount);
        save(uuid);
        return true;
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (!isValidAmount(amount)) return false;
        double current = getBalance(uuid);
        if (current < amount) return false;
        cache.put(uuid, current - amount);
        save(uuid);
        return true;
    }

    public void set(UUID uuid, double amount) {
        if (!Double.isFinite(amount)) return;
        double max = plugin.getConfig().getDouble("economy.max-balance", 999_999_999.0);
        cache.put(uuid, Math.max(0, Math.min(max, amount)));
        save(uuid);
    }

    // ── Classement ───────────────────────────────────────────────────────

    // Cache du classement complet trié, pour éviter de rescanner tout le DataStore à chaque
    // /moneytop. Un scan complet charge depuis le disque CHAQUE joueur connu non déjà en cache
    // mémoire : coûteux et inutile si deux joueurs tapent /moneytop à quelques secondes d'intervalle.
    // Durée réglable via moneytop.cache-seconds (0 = désactive le cache, toujours à jour).
    private volatile List<Map.Entry<String, Double>> topCache;
    private final AtomicLong topCacheAt = new AtomicLong(0L);

    /** Force le prochain /moneytop à rescanner le DataStore plutôt que de servir le cache — utilisé par /money reload. */
    public void invalidateTopCache() {
        topCache = null;
        topCacheAt.set(0L);
    }

    /**
     * Retourne les N entrées les plus riches (tous les joueurs connus, online ou offline).
     * Charge les données de tous les UUID connus depuis le DataStore (avec cache configurable).
     */
    public List<Map.Entry<String, Double>> getTopBalances(int limit) {
        long ttlMs = plugin.getConfig().getLong("moneytop.cache-seconds", 30) * 1000L;
        List<Map.Entry<String, Double>> cached = topCache;
        long now = System.currentTimeMillis();
        if (cached != null && ttlMs > 0 && (now - topCacheAt.get()) < ttlMs) {
            return cached.size() > limit ? cached.subList(0, limit) : cached;
        }

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
                    all.put(name, sanitize(n.doubleValue()));
                }
            } catch (IllegalArgumentException ignored) {}
        }

        List<Map.Entry<String, Double>> sorted = all.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();

        topCache = sorted;
        topCacheAt.set(now);

        return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
    }

    private String nameOf(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }
}
