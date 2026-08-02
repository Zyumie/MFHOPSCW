package fr.zyumie.oxygenArmor;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.oxygenArmor.data.ArmorData;
import fr.zyumie.oxygenArmor.util.ArmorItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArmorManager {

    private static final String NS = "oxygen_armor";

    private final OxygenArmor plugin;
    private final Map<UUID, ArmorData> cache = new ConcurrentHashMap<>();

    public ArmorManager(OxygenArmor plugin) {
        this.plugin = plugin;
    }

    // ─────────────── Persistence ───────────────

    public void loadPlayer(Player player) {
        var raw = MainAPI.get().getDataStore().load(NS, player.getUniqueId());
        double pct = 100.0;
        if (raw != null && raw.get("reserve") instanceof Number n) pct = n.doubleValue();
        cache.put(player.getUniqueId(), new ArmorData(pct));
    }

    public void savePlayer(Player player) {
        ArmorData data = cache.get(player.getUniqueId());
        if (data == null) return;
        MainAPI.get().getDataStore().save(NS, player.getUniqueId(),
                Map.of("reserve", data.getReservePercent()));
    }

    public void unloadPlayer(Player player) {
        savePlayer(player);
        cache.remove(player.getUniqueId());
    }

    public void saveAll() {
        cache.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null)
                .forEach(this::savePlayer);
    }

    // ─────────────── Accesseurs ───────────────

    public ArmorData getData(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), u -> new ArmorData(100.0));
    }

    public double getReserve(Player player) {
        return getData(player).getReservePercent();
    }

    public void setReserve(Player player, double pct) {
        getData(player).setReservePercent(pct);
        syncItemColor(player, pct);
    }

    public void modifyReserve(Player player, double delta) {
        setReserve(player, getReserve(player) + delta);
    }

    public boolean isWearingArmor(Player player) {
        return ArmorItemUtil.isOxygenArmor(plugin, player.getInventory().getChestplate());
    }

    // ─────────────── Privé ───────────────

    /** Met à jour la couleur de l'item porté sans retirer/remettre l'armure (pas de bruit réseau). */
    private void syncItemColor(Player player, double pct) {
        ItemStack chest = player.getInventory().getChestplate();
        if (!ArmorItemUtil.isOxygenArmor(plugin, chest)) return;
        ArmorItemUtil.updatePercent(plugin, chest, pct);
        player.getInventory().setChestplate(chest);
    }
}
