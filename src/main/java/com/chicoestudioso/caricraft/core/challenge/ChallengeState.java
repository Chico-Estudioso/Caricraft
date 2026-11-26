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
    /** Experiencia de desafío acumulada: decide el nivel. */
    long xp;
    /** Guardado antes de que existiera la experiencia: su nivel sale de las noches superadas (una sola vez). */
    boolean legacyLevel;
    /** Jefes derrotados, del primero al último. */
    final java.util.List<BossRecord> bosses = new java.util.ArrayList<>();

    /**
     * Un jefe del desafío derrotado.
     *
     * @param level   nivel del desafío del jefe (5, 10, 15…)
     * @param boss    id del jefe
     * @param players quién estaba en el combate
     */
    public record BossRecord(int level, String boss, long time, String players) {
    }

    public java.util.List<BossRecord> bosses() {
        return bosses;
    }

    public boolean bossDefeated(int level) {
        return bosses.stream().anyMatch(b -> b.level() == level);
    }

    /** Solo para cargar desde disco. */
    public void restoreBosses(java.util.List<BossRecord> records) {
        bosses.clear();
        bosses.addAll(records);
    }

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

    public long xp() {
        return xp;
    }

    /** Solo para cargar desde disco. {@code legacy}: datos de antes de la experiencia. */
    public void restoreXp(long xp, boolean legacy) {
        this.xp = Math.max(0, xp);
        this.legacyLevel = legacy;
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
        this.xp = other.xp;
        restoreBosses(other.bosses);
    }
}
