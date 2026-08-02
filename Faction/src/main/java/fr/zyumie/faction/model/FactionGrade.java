package fr.zyumie.faction.model;

/**
 * Ordre hiérarchique croissant: INVITE (0) → CHEF (4).
 * Le rang numérique sert pour les comparaisons (promote/demote/kick).
 */
public enum FactionGrade {
    INVITE(0),
    MEMBRE(1),
    MODO(2),
    BRAS_DROIT(3),
    CHEF(4);

    private final int rank;

    FactionGrade(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public boolean outranks(FactionGrade other) {
        return this.rank > other.rank;
    }

    public FactionGrade next() {
        FactionGrade[] vals = values();
        if (this.rank + 1 >= vals.length) return this;
        return vals[this.rank + 1];
    }

    public FactionGrade previous() {
        FactionGrade[] vals = values();
        if (this.rank - 1 < 0) return this;
        return vals[this.rank - 1];
    }

    public boolean isMax() { return this == CHEF; }
    public boolean isMin() { return this == INVITE; }
}
