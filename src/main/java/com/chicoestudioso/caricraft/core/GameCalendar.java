package com.chicoestudioso.caricraft.core;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Días y semanas «de verdad» del servidor: cambian a las 00:00 de la zona horaria configurada
 * ({@code timeZone}, por defecto Europe/Madrid), con su horario de verano. Los índices son días desde
 * 1970-01-01, igual que antes (cuando se usaba UTC), así que los datos guardados siguen valiendo.
 */
public final class GameCalendar {
    public static final GameCalendar UTC = new GameCalendar(ZoneOffset.UTC);

    private final ZoneId zone;

    public GameCalendar(ZoneId zone) {
        this.zone = zone;
    }

    /** Zona de la configuración; si no es válida, UTC. */
    public static GameCalendar of(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return UTC;
        }
        try {
            return new GameCalendar(ZoneId.of(zoneId.trim()));
        } catch (DateTimeException e) {
            return UTC;
        }
    }

    public ZoneId zone() {
        return zone;
    }

    /** Día (desde 1970-01-01) en esa zona. */
    public long day(long nowMillis) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(nowMillis), zone).toEpochDay();
    }

    /** Momento en que empieza un día (00:00 local). */
    public long dayStart(long day) {
        return LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Próxima medianoche local. */
    public long nextDayAt(long nowMillis) {
        return dayStart(day(nowMillis) + 1);
    }

    /** Semana que empieza en lunes (1970-01-01 fue jueves: +3). */
    public long week(long nowMillis) {
        return Math.floorDiv(day(nowMillis) + 3, 7);
    }

    /** Próximo lunes a las 00:00 local. */
    public long nextWeekAt(long nowMillis) {
        return dayStart((week(nowMillis) + 1) * 7 - 3);
    }
}
