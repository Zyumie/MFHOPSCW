package fr.zyumie.storm.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Représente l'état complet de la tempête pour la semaine en cours.
 * Supporte désormais une précision à la minute (LocalDateTime).
 */
public class StormState {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private String weekStart; // Format ISO LocalDate (yyyy-MM-dd)
    private String startDate; // Format ISO LocalDateTime
    private String endDate;   // Format ISO LocalDateTime
    private boolean active;
    private boolean triggered;

    public StormState() {}

    public StormState(LocalDate weekStart, LocalDateTime startDate, LocalDateTime endDate) {
        this.weekStart = weekStart.format(DateTimeFormatter.ISO_LOCAL_DATE);
        this.startDate = startDate.format(FMT);
        this.endDate   = endDate.format(FMT);
        this.active    = false;
        this.triggered = false;
    }

    public LocalDate getWeekStart() { return weekStart != null ? LocalDate.parse(weekStart) : null; }
    public LocalDateTime getStartDate() { return startDate != null ? LocalDateTime.parse(startDate, FMT) : null; }
    public LocalDateTime getEndDate()   { return endDate   != null ? LocalDateTime.parse(endDate, FMT)   : null; }
    public boolean isActive()       { return active; }
    public boolean isTriggered()    { return triggered; }

    public void setActive(boolean b)    { this.active    = b; }
    public void setTriggered(boolean b) { this.triggered = b; }

    /** Durée totale en minutes */
    public long getDurationMinutes() {
        if (startDate == null || endDate == null) return 0;
        return java.time.temporal.ChronoUnit.MINUTES.between(getStartDate(), getEndDate());
    }

    /** Durée formatée pour l'affichage (ex: "2 heure(s)", "30 minute(s)") */
    public String getFormattedDuration() {
        long mins = getDurationMinutes();
        if (mins < 60) return mins + " minute(s)";
        long hours = mins / 60;
        if (hours < 24) return hours + " heure(s)";
        long days = hours / 24;
        return days + " jour(s)";
    }

    public String getEndDateFormatted() {
        if (endDate == null) return "N/A";
        LocalDateTime end = getEndDate();
        return end.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRENCH)
                + " " + end.format(DateTimeFormatter.ofPattern("dd/MM à HH:mm"));
    }

    public String getStartDateFormatted() {
        if (startDate == null) return "N/A";
        LocalDateTime start = getStartDate();
        return start.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.FRENCH)
                + " " + start.format(DateTimeFormatter.ofPattern("dd/MM à HH:mm"));
    }
}