package fr.zyumie.main.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stockage MySQL générique: une table unique "main_data" avec colonnes
 * (namespace, data_key, json_data). On sérialise/désérialise la Map<String,Object>
 * en JSON simple maison (pas de dépendance Gson pour rester léger; à remplacer par
 * Gson/Jackson si tu préfères, je peux l'ajouter).
 *
 * Schéma:
 *   CREATE TABLE main_data (
 *     namespace VARCHAR(64) NOT NULL,
 *     data_key  VARCHAR(128) NOT NULL,
 *     json_data TEXT NOT NULL,
 *     PRIMARY KEY (namespace, data_key)
 *   );
 */
public class MySQLDataStore implements DataStore {

    private final Plugin plugin;
    private final HikariDataSource dataSource;
    private final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>(){}.getType();

    public MySQLDataStore(Plugin plugin, ConfigurationSection mysqlConfig) {
        this.plugin = plugin;

        HikariConfig hikariConfig = new HikariConfig();
        String host = mysqlConfig.getString("host", "localhost");
        int port = mysqlConfig.getInt("port", 3306);
        String database = mysqlConfig.getString("database", "mainplugin");
        boolean useSSL = mysqlConfig.getBoolean("useSSL", false);

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=" + useSSL + "&autoReconnect=true&characterEncoding=utf8";

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(mysqlConfig.getString("user", "root"));
        hikariConfig.setPassword(mysqlConfig.getString("password", ""));
        hikariConfig.setMaximumPoolSize(mysqlConfig.getInt("poolSize", 10));
        hikariConfig.setPoolName("MainPlugin-Pool");

        this.dataSource = new HikariDataSource(hikariConfig);
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS main_data (" +
                "namespace VARCHAR(64) NOT NULL," +
                "data_key VARCHAR(128) NOT NULL," +
                "json_data TEXT NOT NULL," +
                "PRIMARY KEY (namespace, data_key))";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Impossible de créer la table main_data: " + e.getMessage());
        }
    }

    @Override
    public void save(String namespace, String key, Map<String, Object> data) {
        String json = gson.toJson(data);
        String sql = "INSERT INTO main_data (namespace, data_key, json_data) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE json_data = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.setString(2, key);
            ps.setString(3, json);
            ps.setString(4, json);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur save MySQL [" + namespace + "/" + key + "]: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> load(String namespace, String key) {
        String sql = "SELECT json_data FROM main_data WHERE namespace = ? AND data_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return gson.fromJson(rs.getString("json_data"), MAP_TYPE);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur load MySQL [" + namespace + "/" + key + "]: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void delete(String namespace, String key) {
        String sql = "DELETE FROM main_data WHERE namespace = ? AND data_key = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur delete MySQL [" + namespace + "/" + key + "]: " + e.getMessage());
        }
    }

    @Override
    public Iterable<String> listKeys(String namespace) {
        List<String> keys = new ArrayList<>();
        String sql = "SELECT data_key FROM main_data WHERE namespace = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, namespace);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) keys.add(rs.getString("data_key"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur listKeys MySQL [" + namespace + "]: " + e.getMessage());
        }
        return keys;
    }

    @Override
    public boolean exists(String namespace, String key) {
        return load(namespace, key) != null;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

}
