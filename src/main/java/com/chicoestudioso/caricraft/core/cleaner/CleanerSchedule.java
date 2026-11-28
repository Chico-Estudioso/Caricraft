package com.chicoestudioso.caricraft.core.cleaner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Cuándo toca limpiar el suelo y qué avisos dar antes, y qué zonas no se tocan (donde alguien ha muerto hace poco,
 * para no borrar lo que soltó). Sin Minecraft: todo en milisegundos y coordenadas.
 */
public final class CleanerSchedule {
    /** Segundos antes de la limpieza en los que se avisa. */
    public static final int[] WARNINGS = {60, 30, 10, 5, 4, 3, 2, 1};

    /** Zona protegida alrededor de una muerte. */
    public record DeathZone(String dimension, double x, double y, double z, long until) {
    }

    private final List<DeathZone> zones = new ArrayList<>();
    private long nextAt;

    /** Programa la siguiente limpieza dentro de {@code intervalMs}. */
    public void schedule(long now, long intervalMs) {
        nextAt = now + intervalMs;
    }

    public long nextAt() {
        return nextAt;
    }

    public boolean due(long now) {
        return nextAt > 0 && now >= nextAt;
    }

    /**
     * Aviso que toca dar en este segundo, o 0 si ninguno. {@code lastWarned} es el último aviso dado (en segundos
     * restantes) para no repetirlo.
     */
    public static int warningFor(long now, long nextAt, int lastWarned) {
        long left = Math.max(0, (nextAt - now + 999) / 1000);
        for (int warning : WARNINGS) {
            if (left == warning && warning != lastWarned) {
                return warning;
            }
        }
        return 0;
    }

    public void protectDeath(String dimension, double x, double y, double z, long until) {
        zones.add(new DeathZone(dimension, x, y, z, until));
    }

    /** ¿Está ese punto cerca de una muerte reciente? */
    public boolean protectedAt(String dimension, double x, double y, double z, double radius, long now) {
        double r2 = radius * radius;
        for (DeathZone zone : zones) {
            if (zone.until() > now && zone.dimension().equals(dimension)) {
                double dx = zone.x() - x;
                double dy = zone.y() - y;
                double dz = zone.z() - z;
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Quita las zonas caducadas. */
    public void purge(long now) {
        for (Iterator<DeathZone> it = zones.iterator(); it.hasNext(); ) {
            if (it.next().until() <= now) {
                it.remove();
            }
        }
    }

    public List<DeathZone> zones() {
        return List.copyOf(zones);
    }
}
