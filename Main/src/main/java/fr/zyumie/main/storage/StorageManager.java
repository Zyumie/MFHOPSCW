package fr.zyumie.main.storage;

import fr.zyumie.main.Main;
import org.bukkit.configuration.ConfigurationSection;

public class StorageManager {

    private final DataStore dataStore;

    public StorageManager(Main plugin) {
        String type = plugin.getConfig().getString("storage.type", "YAML").trim().toUpperCase();

        if (type.equals("MYSQL")) {
            ConfigurationSection mysqlSection = plugin.getConfig().getConfigurationSection("storage.mysql");
            if (mysqlSection == null) {
                plugin.getLogger().severe("storage.type=MYSQL mais storage.mysql est absent de la config. Fallback sur YAML.");
                this.dataStore = new YamlDataStore(plugin);
            } else {
                plugin.getLogger().info("Stockage: MySQL (" + mysqlSection.getString("host") + ":" + mysqlSection.getInt("port") + ")");
                this.dataStore = new MySQLDataStore(plugin, mysqlSection);
            }
        } else {
            plugin.getLogger().info("Stockage: YAML (plugins/MainPlugin/data/)");
            this.dataStore = new YamlDataStore(plugin);
        }
    }

    public DataStore getDataStore() {
        return dataStore;
    }

    public void close() {
        dataStore.close();
    }
}
