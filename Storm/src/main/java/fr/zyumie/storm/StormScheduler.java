package fr.zyumie.storm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.zyumie.storm.data.StormState;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Random;

/**
 * Gère le cycle hebdomadaire de la tempête :
 *  - Génère aléatoirement la durée D et le jour de départ J pour la semaine
 *  - Persiste l'état dans storm_state.json
 *  - Vérifie chaque minute si la tempête doit commencer ou finir
 */
public class StormScheduler {

    private final Storm plugin;
    private final Gson gson;
    private final File stateFile;
    private final Random random;

    private StormState currentState;

    public StormScheduler(Storm plugin) {
        this.plugin    = plugin;
        this.gson      = new GsonBuilder().setPrettyPrinting().create();
        this.stateFile = new File(plugin.getDataFolder(), "storm_state.json");
        this.random    = new Random();
    }

    // ── Initialisation ────────────────────────────────────────────────

    public void init() {
        loadState();
        ensureWeekScheduled();
        startMinuteCheck();
    }

    // ── Persistance ───────────────────────────────────────────────────

    private void loadState() {
        if (!stateFile.exists()) {
            currentState = null;
            return;
        }
        try (FileReader reader = new FileReader(stateFile)) {
            currentState = gson.fromJson(reader, StormState.class);
            plugin.getLogger().info("État de tempête chargé depuis storm_state.json.");
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            plugin.getLogger().warning("Impossible de lire storm_state.json : " + e.getMessage());
            currentState = null;
        }
    }

    private void saveState() {
        if (currentState == null) return;
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        try (FileWriter writer = new FileWriter(stateFile)) {
            gson.toJson(currentState, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder storm_state.json : " + e.getMessage());
        }
    }

    // ── Génération hebdomadaire ───────────────────────────────────────

    /**
     * Si aucun état n'existe ou si l'état appartient à une semaine passée,
     * génère un nouveau planning pour la semaine actuelle.
     */
    private void ensureWeekScheduled() {
        LocalDate today    = LocalDate.now(getZone());
        LocalDate monday   = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        if (currentState != null && monday.equals(currentState.getWeekStart())) {
            // L'état est déjà pour cette semaine — on l'applique si nécessaire
            plugin.getLogger().info("Planning de tempête pour la semaine déjà en place.");
            applyRestoredState();
            return;
        }

        // Nouvelle semaine → générer un nouveau planning
        generateWeeklyStorm(monday);
    }

    /**
     * Algorithme de génération :
     *  1. Tire D ∈ {possible-durations} aléatoirement
     *  2. Liste les jours J valides : J + D - 1 ≤ 7 (dimanche = jour 7)
     *  3. Tire J aléatoirement parmi les jours valides
     */
    private void generateWeeklyStorm(LocalDate monday) {
        List<Integer> durations = plugin.getConfig().getIntegerList("cycle.possible-durations");
        if (durations.isEmpty()) durations = List.of(1, 2, 3, 4, 5);

        // Tire la durée au sort
        int d = durations.get(random.nextInt(durations.size()));

        // Jours valides : J ∈ [1, 7-D+1] (lundi=1, dimanche=7)
        int maxStartDay = 7 - d + 1; // ex: D=5 → maxStartDay=3 (mercredi)
        int startDayOffset = random.nextInt(maxStartDay); // 0-indexed

        LocalDate startDate = monday.plusDays(startDayOffset);
        LocalDate endDate   = startDate.plusDays(d); // exclusif

        currentState = new StormState(monday, startDate, endDate);
        saveState();

        plugin.getLogger().info(String.format(
                "Nouvelle tempête planifiée : début=%s, fin=%s (%d jour(s))",
                startDate, endDate.minusDays(1), d));
    }

    /**
     * À la restauration du serveur : si l'état dit que la tempête est active,
     * on la redémarre immédiatement.
     */
    private void applyRestoredState() {
        if (currentState.isActive()) {
            plugin.getLogger().info("Restauration d'une tempête active — redémarrage des effets.");
            plugin.getStormManager().startStorm(false); // false = pas d'annonce broadcast
        }
    }

    // ── Vérification periodique ───────────────────────────────────────

    /**
     * Tâche qui vérifie chaque minute si la tempête doit changer d'état.
     * Utilise l'heure locale configurée pour le déclenchement.
     */
    private void startMinuteCheck() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (currentState == null) return;

            LocalDate today = LocalDate.now(getZone());
            LocalTime now   = LocalTime.now(getZone());
            LocalTime trigger = getTriggerTime();

            // Nouvelle semaine ?
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (!monday.equals(currentState.getWeekStart())) {
                // Si la tempête était active, on l'arrête proprement
                if (currentState.isActive()) {
                    plugin.getStormManager().stopStorm(true);
                }
                generateWeeklyStorm(monday);
                return;
            }

            boolean shouldBeActive = today.compareTo(currentState.getStartDate()) >= 0
                    && today.isBefore(currentState.getEndDate())
                    && now.compareTo(trigger) >= 0;

            if (shouldBeActive && !currentState.isActive() && !currentState.isTriggered()) {
                // Heure de démarrage
                currentState.setActive(true);
                currentState.setTriggered(true);
                saveState();
                plugin.getStormManager().startStorm(true);

            } else if (!shouldBeActive && currentState.isActive()) {
                // Heure d'arrêt (fin de la durée ou fin de journée de déclenchement)
                boolean stormDone = today.compareTo(currentState.getEndDate()) >= 0;
                if (stormDone) {
                    currentState.setActive(false);
                    saveState();
                    plugin.getStormManager().stopStorm(true);
                }
            }

        }, 20L, 20L * 60L); // vérifie toutes les minutes (1200 ticks)
    }

    // ── API publique ──────────────────────────────────────────────────

    /** Force le démarrage manuel de la tempête. */
    public void forceStart(int durationDays) {
        LocalDate today  = LocalDate.now(getZone());
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end    = today.plusDays(durationDays);

        currentState = new StormState(monday, today, end);
        currentState.setActive(true);
        currentState.setTriggered(true);
        saveState();
    }

    /** Force l'arrêt manuel. */
    public void forceStop() {
        if (currentState != null) {
            currentState.setActive(false);
            saveState();
        }
    }

    public StormState getCurrentState() { return currentState; }

    // ── Helpers ───────────────────────────────────────────────────────

    private ZoneId getZone() {
        String tz = plugin.getConfig().getString("cycle.timezone", "Europe/Paris");
        try { return ZoneId.of(tz); }
        catch (Exception e) { return ZoneId.of("UTC"); }
    }

    private LocalTime getTriggerTime() {
        String raw = plugin.getConfig().getString("cycle.trigger-time", "20:00");
        try { return LocalTime.parse(raw); }
        catch (Exception e) { return LocalTime.of(20, 0); }
    }
}
