package com.chicoestudioso.caricraft.core.routine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Racha diaria, evento semanal y medallas de un grupo. */
public final class RoutineState {
    // Racha
    long streakLastDay = -1;
    int streakDays;
    int streakBest;
    long shieldWeek = Long.MIN_VALUE;
    long duoPaidDay = -1;
    /** Último día en que jugó cada miembro. */
    final Map<UUID, Long> activeDay = new LinkedHashMap<>();
    // Evento semanal
    long eventWeek = Long.MIN_VALUE;
    long eventBaseline;
    /** Tramos cobrados del evento de esta semana (bit 0: mitad, bit 1: completo). */
    int eventPaid;
    // Medallas: id → tramo conseguido (1 bronce, 2 plata, 3 oro)
    final Map<String, Integer> medals = new LinkedHashMap<>();

    public long streakLastDay() {
        return streakLastDay;
    }

    public int streakDays() {
        return streakDays;
    }

    public int streakBest() {
        return streakBest;
    }

    public long shieldWeek() {
        return shieldWeek;
    }

    public long duoPaidDay() {
        return duoPaidDay;
    }

    public Map<UUID, Long> activeDay() {
        return activeDay;
    }

    public long eventWeek() {
        return eventWeek;
    }

    public long eventBaseline() {
        return eventBaseline;
    }

    public int eventPaid() {
        return eventPaid;
    }

    public Map<String, Integer> medals() {
        return medals;
    }

    public void restoreStreak(long lastDay, int days, int best, long shieldWeek, long duoPaidDay, Map<UUID, Long> active) {
        this.streakLastDay = lastDay;
        this.streakDays = Math.max(0, days);
        this.streakBest = Math.max(this.streakDays, best);
        this.shieldWeek = shieldWeek;
        this.duoPaidDay = duoPaidDay;
        this.activeDay.clear();
        this.activeDay.putAll(active);
    }

    public void restoreEvent(long week, long baseline, int paid) {
        this.eventWeek = week;
        this.eventBaseline = baseline;
        this.eventPaid = paid;
    }

    /** Al formar un dúo: se queda la mejor racha y las medallas de los dos. */
    public void mergeFrom(RoutineState other) {
        if (other.streakLastDay > streakLastDay || (other.streakLastDay == streakLastDay && other.streakDays > streakDays)) {
            streakLastDay = other.streakLastDay;
            streakDays = other.streakDays;
            shieldWeek = other.shieldWeek;
        }
        streakBest = Math.max(streakBest, other.streakBest);
        other.activeDay.forEach((member, day) -> activeDay.merge(member, day, Math::max));
        other.medals.forEach((id, tier) -> medals.merge(id, tier, Math::max));
    }

    /** Al separarse: quien se va se lleva una copia de la racha y las medallas. */
    public void copyFrom(RoutineState other) {
        restoreStreak(other.streakLastDay, other.streakDays, other.streakBest, other.shieldWeek, other.duoPaidDay, Map.of());
        medals.putAll(other.medals);
        restoreEvent(other.eventWeek, other.eventBaseline, other.eventPaid);
    }
}
