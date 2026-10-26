package fr.zyumie.home.model;

import org.bukkit.Location;

public class HomeData {
    private final Location location;
    private final long setTime;

    public HomeData(Location location, long setTime) {
        this.location = location;
        this.setTime = setTime;
    }

    public Location getLocation() {
        return location;
    }

    public long getSetTime() {
        return setTime;
    }
}