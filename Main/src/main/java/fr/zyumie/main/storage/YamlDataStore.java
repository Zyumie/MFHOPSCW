package fr.zyumie.main.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class YamlDataStore implements DataStore {

    private final File dataFolder;
    private final Plugin plugin;
    // cache en mémoire: namespace -> YamlConfiguration, pour éviter de relire le disque à chaque appel
    private final Map<String, YamlConfiguration> cache = new ConcurrentHashMap<>();

    public YamlDataStore(Plugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    private File fileFor(String namespace) {
        return new File(dataFolder, namespace + ".yml");
    }

    private YamlConfiguration configFor(String namespace) {
        return cache.computeIfAbsent(namespace, ns -> {
            File f = fileFor(ns);
            if (!f.exists()) {
                try {
                    f.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Impossible de créer " + f.getName() + ": " + e.getMessage());
                }
            }
            return YamlConfiguration.loadConfiguration(f);
        });
    }

    private void persist(String namespace) {
        try {
            configFor(namespace).save(fileFor(namespace));
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur sauvegarde YAML '" + namespace + "': " + e.getMessage());
        }
    }

    @Override
    public synchronized void save(String namespace, String key, Map<String, Object> data) {
        YamlConfiguration cfg = configFor(namespace);
        // on nettoie d'abord la section pour éviter de garder des vieilles clés orphelines
        cfg.set(key, null);
        for (Map.Entry<String, Object> e : data.entrySet()) {
            cfg.set(key + "." + e.getKey(), e.getValue());
        }
        persist(namespace);
    }

    @Override
    public synchronized Map<String, Object> load(String namespace, String key) {
        YamlConfiguration cfg = configFor(namespace);
        var section = cfg.getConfigurationSection(key);
        if (section == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        for (String k : section.getKeys(true)) {
            Object val = section.get(k);
            if (val != null && !(section.isConfigurationSection(k))) {
                result.put(k, val);
            }
        }
        return result.isEmpty() ? new HashMap<>() : result;
    }

    @Override
    public synchronized void delete(String namespace, String key) {
        YamlConfiguration cfg = configFor(namespace);
        cfg.set(key, null);
        persist(namespace);
    }

    @Override
    public synchronized Iterable<String> listKeys(String namespace) {
        YamlConfiguration cfg = configFor(namespace);
        return cfg.getKeys(false);
    }

    @Override
    public synchronized boolean exists(String namespace, String key) {
        return configFor(namespace).isConfigurationSection(key) || configFor(namespace).contains(key);
    }

    @Override
    public void close() {
        // rien à fermer pour YAML, mais on force une dernière sauvegarde de tout le cache
        for (String namespace : cache.keySet()) {
            persist(namespace);
        }
    }
}
