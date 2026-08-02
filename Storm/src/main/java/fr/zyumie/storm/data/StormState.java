package fr.zyumie.storm.data;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Représente l'état complet de la tempête pour la semaine en cours.
 * Sérialisé en JSON dans storm_state.json pour survivre aux redémarrages.
 */
public class StormState {

    // Format ISO 8601 (yyyy-MM-dd) pour la sérialisation
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Lundi de la semaine concernée — sert à détecter un changement de semaine */
    private String weekStart;

    /** Jour de début de la tempête (inclus) */
    private String startDate;

    /** Jour de fin de la tempête (EXCLUSIF — la tempête s'arrête au début de ce jour) */
    private String endDate;

    /** true si la tempête est actuellement active */
    private boolean active;

    /** true si la tempête a déjà été déclenchée cette semaine
     *  (évite de la reprogrammer si elle a été annulée manuellement) */
    private boolean triggered;

    // ── Constructeurs ─────────────────────────────────────────────────

    public StormState() {}

    public StormState(LocalDate weekStart, LocalDate startDate, LocalDate endDate) {
        this.weekStart = weekStart.format(FMT);
        this.startDate = startDate.format(FMT);
        this.endDate   = endDate.format(FMT);
        this.active    = false;
        this.triggered = false;
    }

    // ── Accès typés ───────────────────────────────────────────────────

    public LocalDate getWeekStart() { return weekStart != null ? LocalDate.parse(weekStart, FMT) : null; }
    public LocalDate getStartDate() { return startDate != null ? LocalDate.parse(startDate, FMT) : null; }
    public LocalDate getEndDate()   { return endDate   != null ? LocalDate.parse(endDate,   FMT) : null; }
    public boolean isActive()       { return active; }
    public boolean isTriggered()    { return triggered; }

    public void setActive(boolean b)    { this.active    = b; }
    public void setTriggered(boolean b) { this.triggered = b; }

    /** Durée de la tempête en jours */
    public long getDurationDays() {
        if (startDate == null || endDate == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(getStartDate(), getEndDate());
    }

    /** Fin formatée pour l'affichage (ex: "Mercredi 02/07") */
    public String getEndDateFormatted() {
        if (endDate == null) return "N/A";
        LocalDate end = getEndDate().minusDays(1); // dernier jour inclus
        return end.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)
                + " " + end.format(DateTimeFormatter.ofPattern("dd/MM"));
    }

    public String getStartDateFormatted() {
        if (startDate == null) return "N/A";
        LocalDate start = getStartDate();
        return start.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)
                + " " + start.format(DateTimeFormatter.ofPattern("dd/MM"));
    }
}
