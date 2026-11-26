package com.chicoestudioso.caricraft.server.cobbleverse;

import java.util.concurrent.atomic.AtomicLong;

/** Cuántas apariciones ha dejado, ajustado o cancelado el level cap desde que arrancó el servidor (diagnóstico). */
public final class LevelCapStats {
    public static final AtomicLong KEPT = new AtomicLong();
    public static final AtomicLong CLAMPED = new AtomicLong();
    public static final AtomicLong CANCELLED = new AtomicLong();
    public static final AtomicLong LEGENDS_BLOCKED = new AtomicLong();

    private LevelCapStats() {
    }

    public static String summary() {
        return KEPT.get() + " sin cambios, " + CLAMPED.get() + " bajadas al cap, " + CANCELLED.get() + " canceladas ("
                + LEGENDS_BLOCKED.get() + " legendarios)";
    }
}
