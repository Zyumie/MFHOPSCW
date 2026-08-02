package fr.zyumie.quest;

import fr.zyumie.main.api.MainAPI;
import fr.zyumie.main.storage.DataStore;
import fr.zyumie.quest.model.FactionQuestProgress;
import fr.zyumie.quest.model.QuestDefinition;
import fr.zyumie.quest.model.QuestDefinition.QuestType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QuestManager {

    private static final String NS = "quest_progress";

    private final JavaPlugin plugin;
    private final List<QuestDefinition> allQuests = new ArrayList<>();
    // clé: factionName + ":" + questId
    private final Map<String, FactionQuestProgress> progressCache = new ConcurrentHashMap<>();

    public QuestManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ─────────────── Config loading ───────────────

    public void loadDefinitions() {
        allQuests.clear();
        List<?> list = plugin.getConfig().getList("quests");
        if (list == null) return;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            try {
                String id    = str(map, "id");
                int week     = num(map, "week");
                String title = str(map, "title");
                String desc  = str(map, "description");
                QuestType type = QuestType.valueOf(str(map, "type").toUpperCase());
                String target = map.containsKey("target") ? str(map, "target") : null;
                int amount   = num(map, "amount");
                double money = map.containsKey("reward-money") ? ((Number) map.get("reward-money")).doubleValue() : 0;
                String rmsg  = map.containsKey("reward-message") ? str(map, "reward-message") : "";
                allQuests.add(new QuestDefinition(id, week, title, desc, type, target, amount, money, rmsg));
            } catch (Exception e) {
                plugin.getLogger().warning("Quête mal configurée: " + e.getMessage());
            }
        }
        plugin.getLogger().info("QuestManager: " + allQuests.size() + " quête(s) chargée(s).");
    }

    // ─────────────── Semaine courante ───────────────

    /**
     * Calcule la semaine actuelle dans le cycle (1..cycle-weeks).
     */
    public int getCurrentWeek() {
        String startStr = plugin.getConfig().getString("quest.cycle-start-date", "2025-01-06");
        int cycleWeeks  = plugin.getConfig().getInt("quest.cycle-weeks", 12);
        LocalDate start = LocalDate.parse(startStr);
        LocalDate now   = LocalDate.now();
        long daysPassed = ChronoUnit.DAYS.between(start, now);
        if (daysPassed < 0) return 1;
        long weeksPassed = daysPassed / 7;
        return (int) (weeksPassed % cycleWeeks) + 1;
    }

    /**
     * Retourne les QuestDefinitions actives cette semaine (jusqu'à quests-per-week).
     */
    public List<QuestDefinition> getCurrentWeekQuests() {
        int week = getCurrentWeek();
        int max  = plugin.getConfig().getInt("quest.quests-per-week", 3);
        List<QuestDefinition> result = new ArrayList<>();
        for (QuestDefinition q : allQuests) {
            if (q.getWeek() == week) {
                result.add(q);
                if (result.size() >= max) break;
            }
        }
        return result;
    }

    // ─────────────── Progress ───────────────

    public void loadFactionProgress(String factionName) {
        DataStore store = MainAPI.get().getDataStore();
        for (QuestDefinition q : allQuests) {
            String key = factionName + ":" + q.getId();
            Map<String, Object> raw = store.load(NS, key);
            if (raw == null) continue;
            int prog = raw.get("progress") instanceof Number n ? n.intValue() : 0;
            boolean done = Boolean.parseBoolean(String.valueOf(raw.getOrDefault("completed", false)));
            progressCache.put(key, new FactionQuestProgress(q.getId(), factionName, prog, done));
        }
    }

    public void saveFactionProgress(String factionName) {
        DataStore store = MainAPI.get().getDataStore();
        for (Map.Entry<String, FactionQuestProgress> e : progressCache.entrySet()) {
            if (!e.getValue().getFactionName().equals(factionName)) continue;
            store.save(NS, e.getKey(), Map.of(
                    "progress",  e.getValue().getProgress(),
                    "completed", e.getValue().isCompleted()
            ));
        }
    }

    public void saveAll() {
        DataStore store = MainAPI.get().getDataStore();
        for (Map.Entry<String, FactionQuestProgress> e : progressCache.entrySet()) {
            store.save(NS, e.getKey(), Map.of(
                    "progress",  e.getValue().getProgress(),
                    "completed", e.getValue().isCompleted()
            ));
        }
    }

    public FactionQuestProgress getProgress(String factionName, String questId) {
        String key = factionName + ":" + questId;
        return progressCache.computeIfAbsent(key,
                k -> new FactionQuestProgress(questId, factionName));
    }

    /**
     * Ajoute du progrès pour toutes les quêtes actives d'une faction qui correspondent
     * au type + target donnés.
     * @return liste des quêtes complétées ce tick (pour déclencher la récompense)
     */
    public List<QuestDefinition> addProgress(String factionName, QuestType type,
                                             String target, int amount) {
        List<QuestDefinition> completed = new ArrayList<>();
        for (QuestDefinition q : getCurrentWeekQuests()) {
            if (q.getType() != type) continue;
            if (target != null && q.getTarget() != null
                    && !q.getTarget().equalsIgnoreCase(target)) continue;

            FactionQuestProgress prog = getProgress(factionName, q.getId());
            if (prog.isCompleted()) continue;
            prog.addProgress(amount);
            if (prog.getProgress() >= q.getAmount()) {
                prog.setCompleted();
                completed.add(q);
            }
        }
        return completed;
    }

    // ─────────────── Helpers ───────────────

    private String str(Map<?, ?> m, String k) { return String.valueOf(m.get(k)); }
    private int num(Map<?, ?> m, String k) { return ((Number) m.get(k)).intValue(); }
}
