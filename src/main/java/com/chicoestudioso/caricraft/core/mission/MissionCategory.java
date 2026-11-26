package com.chicoestudioso.caricraft.core.mission;

public enum MissionCategory {
    /** Una sola vez. */
    STORY,
    /** Se activa sola; una sola vez. */
    MILESTONE,
    /** Se reinicia cada día (00:00 de la zona configurada). */
    DAILY,
    /** Se reinicia cada semana (lunes, 00:00 de la zona configurada). */
    WEEKLY;

    public boolean repeatable() {
        return this == DAILY || this == WEEKLY;
    }

    /** Identificador del ciclo actual (en UTC). 0 para misiones no repetibles. */
    public long cycle(long nowMillis) {
        return cycle(nowMillis, com.chicoestudioso.caricraft.core.GameCalendar.UTC);
    }

    /** Identificador del ciclo actual con el calendario del servidor. 0 para misiones no repetibles. */
    public long cycle(long nowMillis, com.chicoestudioso.caricraft.core.GameCalendar calendar) {
        return switch (this) {
            case DAILY -> calendar.day(nowMillis);
            case WEEKLY -> calendar.week(nowMillis);
            default -> 0;
        };
    }

    /** Momento en que termina el ciclo actual (en UTC), o {@code 0} si no caduca. */
    public long cycleEnd(long nowMillis) {
        return cycleEnd(nowMillis, com.chicoestudioso.caricraft.core.GameCalendar.UTC);
    }

    /** Momento en que termina el ciclo actual con el calendario del servidor, o {@code 0} si no caduca. */
    public long cycleEnd(long nowMillis, com.chicoestudioso.caricraft.core.GameCalendar calendar) {
        return switch (this) {
            case DAILY -> calendar.nextDayAt(nowMillis);
            case WEEKLY -> calendar.nextWeekAt(nowMillis);
            default -> 0;
        };
    }
}
