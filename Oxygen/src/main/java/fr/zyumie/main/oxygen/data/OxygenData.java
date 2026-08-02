package fr.zyumie.main.oxygen.data;

public class OxygenData {

    private double percent;
    private boolean suffocating;
    private long lastDamageTickMillis;

    public OxygenData(double percent) {
        this.percent = percent;
        this.suffocating = percent <= 0.0;
        this.lastDamageTickMillis = 0L;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        this.percent = Math.max(0.0, Math.min(100.0, percent));
    }

    public boolean isSuffocating() {
        return suffocating;
    }

    public void setSuffocating(boolean suffocating) {
        this.suffocating = suffocating;
    }

    public long getLastDamageTickMillis() {
        return lastDamageTickMillis;
    }

    public void setLastDamageTickMillis(long lastDamageTickMillis) {
        this.lastDamageTickMillis = lastDamageTickMillis;
    }
}
