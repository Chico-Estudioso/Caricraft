package com.chicoestudioso.caricraft.core.challenge;

/** Estado del Desafío Dúo de un grupo. Los "días" son índices de noche (ver {@link ChallengeService}). */
public final class ChallengeState {
    public static final long UNSET = Long.MIN_VALUE;
    public static final long NONE = -1;

    /** Día de la última noche de desafío completada, o {@link #UNSET} si el grupo aún no ha jugado. */
    long lastChallengeDay = UNSET;
    /** Día cuya noche será (o es) de desafío, o {@link #NONE}. */
    long scheduledDay = NONE;
    /** Si el desafío programado lo impuso la regla de "cada N noches" (y no el dúo). */
    boolean forced;
    boolean active;
    int nightsCompleted;
    long totalKills;
    long totalCarins;
    int nightKills;
    long nightCarins;

    public long lastChallengeDay() {
        return lastChallengeDay;
    }

    public long scheduledDay() {
        return scheduledDay;
    }

    public boolean forced() {
        return forced;
    }

    public boolean active() {
        return active;
    }

    public int nightsCompleted() {
        return nightsCompleted;
    }

    public long totalKills() {
        return totalKills;
    }

    public long totalCarins() {
        return totalCarins;
    }

    public int nightKills() {
        return nightKills;
    }

    public long nightCarins() {
        return nightCarins;
    }

    /** Solo para cargar desde disco. */
    public void restore(long lastChallengeDay, long scheduledDay, boolean forced, boolean active, int nightsCompleted,
                        long totalKills, long totalCarins, int nightKills, long nightCarins) {
        this.lastChallengeDay = lastChallengeDay;
        this.scheduledDay = scheduledDay;
        this.forced = forced;
        this.active = active;
        this.nightsCompleted = Math.max(0, nightsCompleted);
        this.totalKills = Math.max(0, totalKills);
        this.totalCarins = Math.max(0, totalCarins);
        this.nightKills = Math.max(0, nightKills);
        this.nightCarins = Math.max(0, nightCarins);
    }

    /** Copia los contadores al separar un dúo. */
    public void copyFrom(ChallengeState other) {
        restore(other.lastChallengeDay, NONE, false, false, other.nightsCompleted, other.totalKills, other.totalCarins, 0, 0);
    }
}
