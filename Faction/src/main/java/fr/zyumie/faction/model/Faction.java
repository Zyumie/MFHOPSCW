package fr.zyumie.faction.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Faction {

    private final String name;
    private final Map<UUID, FactionGrade> members = new HashMap<>();
    private double bankBalance;
    private boolean pvpEnabled; // Faux par défaut
    private final Set<UUID> pendingInvites = new HashSet<>();

    // Création d'une faction (fondateur)
    public Faction(String name, UUID founderUuid) {
        this.name = name;
        this.bankBalance = 0.0;
        this.pvpEnabled = false;
        this.members.put(founderUuid, FactionGrade.CHEF);
    }

    // Reconstruction depuis la persistance (chargement)
    public Faction(String name, double bankBalance, boolean pvpEnabled, Map<UUID, FactionGrade> members) {
        this.name = name;
        this.bankBalance = bankBalance;
        this.pvpEnabled = pvpEnabled;
        this.members.putAll(members);
    }

    public String getName() { return name; }
    public double getBankBalance() { return bankBalance; }
    public void setBankBalance(double bankBalance) { this.bankBalance = Math.max(0, bankBalance); }
    public void depositBank(double amount) { this.bankBalance += amount; }
    public boolean withdrawBank(double amount) {
        if (bankBalance < amount) return false;
        bankBalance -= amount;
        return true;
    }

    // --- PvP ---
    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }

    // --- Membres ---
    public Map<UUID, FactionGrade> getMembers() { return Collections.unmodifiableMap(members); }
    public int getMemberCount() { return members.size(); }

    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }
    public FactionGrade getGrade(UUID uuid) { return members.getOrDefault(uuid, null); }
    public boolean isChef(UUID uuid) { return FactionGrade.CHEF.equals(members.get(uuid)); }

    public void addMember(UUID uuid, FactionGrade grade) { members.put(uuid, grade); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public void setGrade(UUID uuid, FactionGrade grade) { members.put(uuid, grade); }

    public UUID getChef() {
        return members.entrySet().stream()
                .filter(e -> e.getValue() == FactionGrade.CHEF)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    // --- Invitations ---
    public void addInvite(UUID uuid) { pendingInvites.add(uuid); }
    public void removeInvite(UUID uuid) { pendingInvites.remove(uuid); }
    public boolean isInvited(UUID uuid) { return pendingInvites.contains(uuid); }
}