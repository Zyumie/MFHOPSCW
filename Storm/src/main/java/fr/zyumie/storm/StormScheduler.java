package fr.zyumie.storm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.zyumie.storm.data.StormState;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Random;

/**
 * Gère le cycle de la tempête avec support des minutes, heures et jours.
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
        this.stateFile = new File(plugin.getCustomDataFolder(), "storm_state.json");
        this.random    = new Random();
    }

    public void init() {
        loadState();
        ensureWeekScheduled();
        startMinuteCheck();
    }

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

    private void ensureWeekScheduled() {
        LocalDate today  = LocalDate.now(getZone());
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        if (currentState != null && monday.equals(currentState.getWeekStart())) {
            plugin.getLogger().info("Planning de tempête pour la semaine déjà en place.");
            applyRestoredState();
            return;
        }

        generateWeeklyStorm(monday);
    }

    private void generateWeeklyStorm(LocalDate monday) {
        List<String> rawDurations = plugin.getConfig().getStringList("cycle.possible-durations");
        if (rawDurations.isEmpty()) rawDurations = List.of("2h", "6h", "1d", "2d");

        // Tire une durée aléatoire sous forme de texte (ex: "30m", "2h", "1d")
        String randomDurationStr = rawDurations.get(random.nextInt(rawDurations.size()));
        Duration duration = parseDuration(randomDurationStr);

        // Choisit un jour aléatoire dans la semaine (lundi à dimanche) et l'heure de déclenchement
        int randomDayOffset = random.nextInt(7);
        LocalDate stormDate = monday.plusDays(randomDayOffset);
        LocalTime triggerTime = getTriggerTime();

        LocalDateTime startDateTime = LocalDateTime.of(stormDate, triggerTime);
        LocalDateTime endDateTime   = startDateTime.plus(duration);

        currentState = new StormState(monday, startDateTime, endDateTime);
        saveState();

        plugin.getLogger().info(String.format(
                "Nouvelle tempête planifiée : début=%s, fin=%s (%s)",
                startDateTime, endDateTime, randomDurationStr));
    }

    private void applyRestoredState() {
        if (currentState.isActive()) {
            plugin.getLogger().info("Restauration d'une tempête active — redémarrage des effets.");
            plugin.getStormManager().startStorm(false);
        }
    }

    private void startMinuteCheck() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (currentState == null) return;

            LocalDateTime now = LocalDateTime.now(getZone());
            LocalDate today = now.toLocalDate();
            LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            if (!monday.equals(currentState.getWeekStart())) {
                if (currentState.isActive()) {
                    plugin.getStormManager().stopStorm(true);
                }
                generateWeeklyStorm(monday);
                return;
            }

            boolean shouldBeActive = !now.isBefore(currentState.getStartDate())
                    && now.isBefore(currentState.getEndDate());

            if (shouldBeActive && !currentState.isActive() && !currentState.isTriggered()) {
                currentState.setActive(true);
                currentState.setTriggered(true);
                saveState();
                plugin.getStormManager().startStorm(true);

            } else if (!shouldBeActive && currentState.isActive()) {
                if (!now.isBefore(currentState.getEndDate())) {
                    currentState.setActive(false);
                    saveState();
                    plugin.getStormManager().stopStorm(true);
                }
            }

        }, 20L, 20L * 60L);
    }

    /** Permet de forcer une tempête avec une durée flexible (ex: "30m", "2h", "1d") */
    public void forceStart(String durationStr) {
        Duration duration = parseDuration(durationStr);
        LocalDateTime now = LocalDateTime.now(getZone());
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime end = now.plus(duration);

        currentState = new StormState(monday, now, end);
        currentState.setActive(true);
        currentState.setTriggered(true);
        saveState();
    }

    public void forceStop() {
        if (currentState != null) {
            currentState.setActive(false);
            saveState();
        }
    }

    public StormState getCurrentState() { return currentState; }

    /** Analyse une chaîne de caractères de durée (ex: 30m, 2h, 1d) */
    public static Duration parseDuration(String input) {
        if (input == null || input.isEmpty()) return Duration.ofHours(1);
        input = input.toLowerCase().trim();
        char unit = input.charAt(input.length() - 1);
        String numberPart = input.substring(0, input.length() - 1);
        try {
            long val = Long.parseLong(numberPart);
            return switch (unit) {
                case 's' -> Duration.ofSeconds(val);
                case 'm' -> Duration.ofMinutes(val);
                case 'h' -> Duration.ofHours(val);
                case 'd' -> Duration.ofDays(val);
                default -> Duration.ofHours(Long.parseLong(input));
            };
        } catch (NumberFormatException e) {
            try {
                long val = Long.parseLong(input);
                return Duration.ofHours(val);
            } catch (NumberFormatException ex) {
                return Duration.ofHours(1);
            }
        }
    }

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