package fr.zyumie.faction.listener;

import fr.zyumie.faction.model.Faction;
import fr.zyumie.faction.model.FactionGrade;
import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FactionManager {

    private static final String NS_FACTION = "faction";

    private final JavaPlugin plugin;
    private final Map<String, Faction> factions = new ConcurrentHashMap<>();
    private final Map<UUID, String> memberIndex = new ConcurrentHashMap<>();

    public FactionManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        DataStore store = MainAPI.get().getDataStore();

        for (String key : store.listKeys(NS_FACTION)) {
            Map<String, Object> raw = store.load(NS_FACTION, key);
            if (raw == null) continue;

            double bank = raw.get("bank") instanceof Number n ? n.doubleValue() : 0.0;
            boolean pvp = raw.get("pvp") instanceof Boolean b ? b : false;

            Map<UUID, FactionGrade> members = new HashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (!e.getKey().startsWith("member.")) continue;
                try {
                    UUID uuid = UUID.fromString(e.getKey().substring(7));
                    FactionGrade grade = FactionGrade.valueOf(String.valueOf(e.getValue()));
                    members.put(uuid, grade);
                } catch (Exception ignored) {}
            }

            if (members.isEmpty()) continue;
            Faction faction = new Faction(key, bank, pvp, members);
            factions.put(key.toLowerCase(), faction);
            members.keySet().forEach(uuid -> memberIndex.put(uuid, key.toLowerCase()));
        }
        plugin.getLogger().info("FactionManager: " + factions.size() + " faction(s) chargée(s).");
    }

    public void saveFaction(Faction faction) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", faction.getName());
        data.put("bank", faction.getBankBalance());
        data.put("pvp", faction.isPvpEnabled());
        for (Map.Entry<UUID, FactionGrade> e : faction.getMembers().entrySet()) {
            data.put("member." + e.getKey(), e.getValue().name());
        }
        MainAPI.get().getDataStore().save(NS_FACTION, faction.getName().toLowerCase(), data);
    }

    public void deleteFaction(Faction faction) {
        MainAPI.get().getDataStore().delete(NS_FACTION, faction.getName().toLowerCase());
    }

    public void saveAll() {
        factions.values().forEach(this::saveFaction);
    }

    public Faction create(String name, UUID founderUuid) {
        Faction f = new Faction(name, founderUuid);
        factions.put(name.toLowerCase(), f);
        memberIndex.put(founderUuid, name.toLowerCase());
        saveFaction(f);
        return f;
    }

    public void disband(Faction faction) {
        faction.getMembers().keySet().forEach(memberIndex::remove);
        factions.remove(faction.getName().toLowerCase());
        deleteFaction(faction);
    }

    public void addMember(Faction faction, UUID uuid, FactionGrade grade) {
        faction.addMember(uuid, grade);
        memberIndex.put(uuid, faction.getName().toLowerCase());
        saveFaction(faction);
    }

    public void removeMember(Faction faction, UUID uuid) {
        faction.removeMember(uuid);
        memberIndex.remove(uuid);
        saveFaction(faction);
    }

    public void setGrade(Faction faction, UUID uuid, FactionGrade grade) {
        faction.setGrade(uuid, grade);
        saveFaction(faction);
    }

    public void setPvp(Faction faction, boolean pvp) {
        faction.setPvpEnabled(pvp);
        saveFaction(faction);
    }

    public Faction getFaction(String name) {
        return factions.get(name.toLowerCase());
    }

    public Faction getFactionOf(UUID uuid) {
        String key = memberIndex.get(uuid);
        return key != null ? factions.get(key) : null;
    }

    public boolean exists(String name) {
        return factions.containsKey(name.toLowerCase());
    }

    public boolean isInFaction(UUID uuid) {
        return memberIndex.containsKey(uuid);
    }

    public Collection<Faction> getAllFactions() {
        return factions.values();
    }
}