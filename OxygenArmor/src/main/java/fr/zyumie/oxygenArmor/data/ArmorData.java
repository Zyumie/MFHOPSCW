package fr.zyumie.oxygenArmor.data;

public class ArmorData {

    private double reservePercent;

    public ArmorData(double reservePercent) {
        this.reservePercent = Math.max(0, Math.min(100, reservePercent));
    }

    public double getReservePercent() { return reservePercent; }

    public void setReservePercent(double value) {
        this.reservePercent = Math.max(0, Math.min(100, value));
    }
}
