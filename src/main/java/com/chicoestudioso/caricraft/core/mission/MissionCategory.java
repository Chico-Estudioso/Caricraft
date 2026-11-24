package com.chicoestudioso.caricraft.core.mission;

public enum MissionCategory {
    /** Una sola vez. */
    STORY,
    /** Se activa sola; una sola vez. */
    MILESTONE,
    /** Se reinicia cada día (UTC). */
    DAILY,
    /** Se reinicia cada semana (lunes, UTC). */
    WEEKLY;

    private static final long DAY_MS = 86_400_000L;

    public boolean repeatable() {
        return this == DAILY || this == WEEKLY;
    }

    /** Identificador del ciclo actual. 0 para misiones no repetibles. */
    public long cycle(long nowMillis) {
        long day = Math.floorDiv(nowMillis, DAY_MS);
        return switch (this) {
            case DAILY -> day;
            // 1970-01-01 fue jueves: +3 hace que las semanas empiecen en lunes.
            case WEEKLY -> Math.floorDiv(day + 3, 7);
            default -> 0;
        };
    }

    /** Momento en que termina el ciclo actual, o {@code 0} si no caduca. */
    public long cycleEnd(long nowMillis) {
        return switch (this) {
            case DAILY -> (cycle(nowMillis) + 1) * DAY_MS;
            case WEEKLY -> ((cycle(nowMillis) + 1) * 7 - 3) * DAY_MS;
            default -> 0;
        };
    }
}
